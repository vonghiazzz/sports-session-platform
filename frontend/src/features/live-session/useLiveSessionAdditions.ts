import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef, useState } from 'react'
import type {
  CourtResponse,
  CreateCourtRequest,
  CreatePlayerRequest,
  PlayerResponse,
  SessionCourtResponse,
  SessionParticipantResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  addSessionCourt,
  addSessionParticipant,
  createCourt,
  createPlayer,
  getSetupSessionCourts,
  getSetupSessionParticipants,
  getSetupVenueCourts,
} from '../../api/sessionSetupApi'

function appendById<T extends { readonly id: string }>(
  current: readonly T[] | undefined,
  item: T,
): readonly T[] {
  if (current?.some((candidate) => candidate.id === item.id)) {
    return current
  }
  return [...(current ?? []), item]
}

async function readParticipants(
  queryClient: ReturnType<typeof useQueryClient>,
  sessionId: string,
): Promise<readonly SessionParticipantResponse[]> {
  const participants = await getSetupSessionParticipants(sessionId)
  queryClient.setQueryData(
    ['sessionParticipants', sessionId],
    participants,
  )
  return participants
}

async function readSessionCourts(
  queryClient: ReturnType<typeof useQueryClient>,
  sessionId: string,
): Promise<readonly SessionCourtResponse[]> {
  const courts = await getSetupSessionCourts(sessionId)
  queryClient.setQueryData(['sessionCourts', sessionId], courts)
  return courts
}

async function readVenueCourts(
  queryClient: ReturnType<typeof useQueryClient>,
  venueId: string,
): Promise<readonly CourtResponse[]> {
  const courts = await getSetupVenueCourts(venueId)
  queryClient.setQueryData(['venueCourts', venueId], courts)
  return courts
}

type PlayerCommand =
  | { readonly type: 'existing'; readonly player: PlayerResponse }
  | { readonly type: 'create'; readonly request: CreatePlayerRequest }
  | { readonly type: 'retry-created'; readonly player: PlayerResponse }

interface PlayerCommandResult {
  readonly added: boolean
  readonly player: PlayerResponse | null
}

export function useLiveAddPlayer(sessionId: string) {
  const queryClient = useQueryClient()
  const inFlight = useRef(false)
  const recoveryPlayerRef = useRef<PlayerResponse | null>(null)
  const [recoveryPlayer, setRecoveryPlayer] = useState<PlayerResponse | null>(null)
  const [unknownPlayerId, setUnknownPlayerId] = useState<string | null>(null)
  const [createOutcomeUnknown, setCreateOutcomeUnknown] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  async function addAndReconcile(
    player: PlayerResponse,
  ): Promise<PlayerCommandResult> {
    let createdParticipant: SessionParticipantResponse | null = null
    let postError: unknown = null
    try {
      createdParticipant = await addSessionParticipant(sessionId, {
        playerId: player.id,
      })
    } catch (error) {
      postError = error
    }

    try {
      const participants = await readParticipants(queryClient, sessionId)
      if (participants.some((participant) => participant.playerId === player.id)) {
        setUnknownPlayerId(null)
        setMessage('Đã thêm người chơi vào phiên. Có thể điểm danh trong danh sách Đã đăng ký.')
        return { added: true, player }
      }
      setUnknownPlayerId(null)
      setMessage(
        postError instanceof HttpError
          ? 'Không thể thêm người chơi vào phiên.'
          : 'Máy chủ xác nhận người chơi chưa được thêm. Bạn có thể chủ động thử lại.',
      )
      return { added: false, player }
    } catch {
      if (createdParticipant !== null) {
        queryClient.setQueryData<readonly SessionParticipantResponse[]>(
          ['sessionParticipants', sessionId],
          (current) => appendById(current, createdParticipant),
        )
        void queryClient.invalidateQueries({
          queryKey: ['sessionParticipants', sessionId],
          exact: true,
        })
        setMessage('Đã thêm người chơi; danh sách đang được đồng bộ lại.')
        return { added: true, player }
      }
      setUnknownPlayerId(player.id)
      setMessage(
        'Chưa thể xác định người chơi đã được thêm hay chưa. Hãy kiểm tra lại trạng thái trước khi gửi lại.',
      )
      return { added: false, player }
    }
  }

  const mutation = useMutation({
    mutationFn: async (command: PlayerCommand): Promise<PlayerCommandResult> => {
      let player: PlayerResponse
      if (command.type === 'create') {
        try {
          player = await createPlayer(command.request)
        } catch (error) {
          void queryClient.invalidateQueries({ queryKey: ['players'], exact: true })
          if (!(error instanceof HttpError)) {
            setCreateOutcomeUnknown(true)
            setMessage(
              'Mất kết nối khi tạo người chơi nên chưa rõ kết quả. Danh sách đang được tải lại; không tạo lại để tránh trùng.',
            )
          } else {
            setMessage('Không thể tạo người chơi. Hãy kiểm tra thông tin và thử lại.')
          }
          return { added: false, player: null }
        }
        queryClient.setQueryData<readonly PlayerResponse[]>(
          ['players'],
          (current) => appendById(current, player),
        )
        recoveryPlayerRef.current = player
        setRecoveryPlayer(player)
      } else {
        player = command.player
      }

      const result = await addAndReconcile(player)
      if (result.added) {
        recoveryPlayerRef.current = null
        setRecoveryPlayer(null)
      }
      return result
    },
    retry: false,
  })

  const execute = useCallback(
    async (command: PlayerCommand): Promise<boolean> => {
      if (inFlight.current || unknownPlayerId !== null) {
        return false
      }
      inFlight.current = true
      setMessage(null)
      try {
        return (await mutation.mutateAsync(command)).added
      } finally {
        inFlight.current = false
      }
    },
    [mutation, unknownPlayerId],
  )

  const reconcileUnknown = useCallback(async (): Promise<boolean> => {
    const playerId = unknownPlayerId
    if (playerId === null || inFlight.current) {
      return false
    }
    inFlight.current = true
    try {
      const participants = await readParticipants(queryClient, sessionId)
      const added = participants.some((participant) => participant.playerId === playerId)
      setUnknownPlayerId(null)
      setMessage(
        added
          ? 'Người chơi đã có trong phiên.'
          : 'Người chơi chưa có trong phiên. Bạn có thể chủ động thử lại.',
      )
      if (added) {
        recoveryPlayerRef.current = null
        setRecoveryPlayer(null)
      }
      return added
    } catch {
      setMessage('Vẫn chưa thể kiểm tra trạng thái. Chưa gửi lại yêu cầu thêm người chơi.')
      return false
    } finally {
      inFlight.current = false
    }
  }, [queryClient, sessionId, unknownPlayerId])

  return {
    addExistingPlayer: (player: PlayerResponse) =>
      execute({ type: 'existing', player }),
    createAndAddPlayer: (request: CreatePlayerRequest) =>
      execute({ type: 'create', request }),
    retryCreatedPlayer: () => {
      const player = recoveryPlayerRef.current
      return player === null
        ? Promise.resolve(false)
        : execute({ type: 'retry-created', player })
    },
    reconcileUnknown,
    isPending: mutation.isPending,
    recoveryPlayer,
    hasUnknownAddOutcome: unknownPlayerId !== null,
    createOutcomeUnknown,
    message,
  }
}

type CourtCommand =
  | { readonly type: 'existing'; readonly court: CourtResponse }
  | {
      readonly type: 'create'
      readonly venueId: string
      readonly request: CreateCourtRequest
    }
  | { readonly type: 'retry-created'; readonly court: CourtResponse }

type CourtAllocationOutcome = 'added' | 'not-added' | 'unknown'

interface CourtCommandResult {
  readonly added: boolean
  readonly court: CourtResponse | null
}

interface UnknownCourtCreation {
  readonly venueId: string
  readonly request: CreateCourtRequest
}

export function useLiveAddCourt(sessionId: string) {
  const queryClient = useQueryClient()
  const inFlight = useRef(false)
  const recoveryCourtRef = useRef<CourtResponse | null>(null)
  const [recoveryCourt, setRecoveryCourt] = useState<CourtResponse | null>(null)
  const [unknownCourtId, setUnknownCourtId] = useState<string | null>(null)
  const [unknownCreation, setUnknownCreation] =
    useState<UnknownCourtCreation | null>(null)
  const [pendingStage, setPendingStage] =
    useState<'CREATING' | 'ALLOCATING' | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  async function addAndReconcile(
    court: CourtResponse,
    cachePostResponseOnReadFailure: boolean,
  ): Promise<CourtAllocationOutcome> {
    let createdSessionCourt: SessionCourtResponse | null = null
    let postError: unknown = null
    try {
      createdSessionCourt = await addSessionCourt(sessionId, {
        courtId: court.id,
      })
    } catch (error) {
      postError = error
    }

    try {
      const sessionCourts = await readSessionCourts(queryClient, sessionId)
      if (sessionCourts.some((candidate) => candidate.courtId === court.id)) {
        setUnknownCourtId(null)
        setMessage('Đã thêm sân vào phiên.')
        return 'added'
      }
      setUnknownCourtId(null)
      setMessage(
        postError instanceof HttpError
          ? 'Không thể thêm sân vào phiên.'
          : 'Máy chủ xác nhận sân chưa được thêm. Bạn có thể chủ động thử lại.',
      )
      return 'not-added'
    } catch {
      if (createdSessionCourt !== null) {
        if (cachePostResponseOnReadFailure) {
          queryClient.setQueryData<readonly SessionCourtResponse[]>(
            ['sessionCourts', sessionId],
            (current) => appendById(current, createdSessionCourt),
          )
        }
        void queryClient.invalidateQueries({
          queryKey: ['sessionCourts', sessionId],
          exact: true,
        })
        setMessage('Đã thêm sân; danh sách đang được đồng bộ lại.')
        return 'added'
      }
      setUnknownCourtId(court.id)
      setMessage(
        'Chưa thể xác định sân đã được thêm hay chưa. Hãy kiểm tra lại trạng thái trước khi gửi lại.',
      )
      return 'unknown'
    }
  }

  const mutation = useMutation({
    mutationFn: async (command: CourtCommand): Promise<CourtCommandResult> => {
      let court: CourtResponse
      const createdNow = command.type === 'create'

      if (command.type === 'create') {
        setPendingStage('CREATING')
        try {
          court = await createCourt(command.venueId, command.request)
        } catch (error) {
          await queryClient.invalidateQueries({
            queryKey: ['venueCourts', command.venueId],
            exact: true,
          })
          if (error instanceof HttpError) {
            setMessage(
              error.status === 409
                ? 'Không thể tạo sân vì tên sân đã tồn tại hoặc dữ liệu không còn hợp lệ.'
                : 'Không thể tạo sân. Hãy kiểm tra thông tin và thử lại.',
            )
          } else {
            setUnknownCreation({
              venueId: command.venueId,
              request: command.request,
            })
            setMessage(
              'Mất kết nối nên chưa thể xác định sân đã được tạo hay chưa. Hãy kiểm tra lại danh sách trước khi tạo lại.',
            )
          }
          return { added: false, court: null }
        }

        try {
          await readVenueCourts(queryClient, command.venueId)
        } catch {
          void queryClient.invalidateQueries({
            queryKey: ['venueCourts', command.venueId],
            exact: true,
          })
        }
      } else {
        court = command.court
      }

      setPendingStage('ALLOCATING')
      const outcome = await addAndReconcile(court, !createdNow)
      if (outcome === 'added') {
        recoveryCourtRef.current = null
        setRecoveryCourt(null)
        return { added: true, court }
      }
      if (createdNow) {
        recoveryCourtRef.current = court
        setRecoveryCourt(court)
        setMessage(
          outcome === 'not-added'
            ? 'Sân đã được tạo nhưng chưa thể thêm vào phiên. Bạn có thể chọn sân này và thử thêm lại.'
            : 'Sân đã được tạo nhưng chưa thể xác định đã thêm vào phiên hay chưa. Hãy kiểm tra lại trạng thái trước khi thử thêm.',
        )
      }
      return { added: false, court }
    },
    retry: false,
  })

  const execute = useCallback(
    async (command: CourtCommand): Promise<boolean> => {
      if (
        inFlight.current ||
        unknownCourtId !== null ||
        unknownCreation !== null
      ) {
        return false
      }
      inFlight.current = true
      setMessage(null)
      try {
        return (await mutation.mutateAsync(command)).added
      } finally {
        setPendingStage(null)
        inFlight.current = false
      }
    },
    [mutation, unknownCourtId, unknownCreation],
  )

  const reconcileUnknown = useCallback(async (): Promise<boolean> => {
    const courtId = unknownCourtId
    if (courtId === null || inFlight.current) {
      return false
    }
    inFlight.current = true
    try {
      const courts = await readSessionCourts(queryClient, sessionId)
      const added = courts.some((court) => court.courtId === courtId)
      setUnknownCourtId(null)
      const createdCourt = recoveryCourtRef.current
      setMessage(
        added
          ? 'Sân đã có trong phiên.'
          : createdCourt === null
            ? 'Sân chưa có trong phiên. Bạn có thể chủ động thử lại.'
            : 'Sân đã được tạo nhưng chưa có trong phiên. Bạn có thể chọn sân này và thử thêm lại.',
      )
      if (added) {
        recoveryCourtRef.current = null
        setRecoveryCourt(null)
      }
      return added
    } catch {
      setMessage('Vẫn chưa thể kiểm tra trạng thái. Chưa gửi lại yêu cầu thêm sân.')
      return false
    } finally {
      inFlight.current = false
    }
  }, [queryClient, sessionId, unknownCourtId])

  const reconcileUnknownCreation = useCallback(async (): Promise<boolean> => {
    const pendingCreation = unknownCreation
    if (pendingCreation === null || inFlight.current) {
      return false
    }
    inFlight.current = true
    try {
      const courts = await readVenueCourts(
        queryClient,
        pendingCreation.venueId,
      )
      const expectedName = pendingCreation.request.name.trim()
      const created = courts.find(
        (court) =>
          court.venueId === pendingCreation.venueId &&
          court.name === expectedName &&
          court.sport === pendingCreation.request.sport &&
          court.active === pendingCreation.request.active,
      )
      setUnknownCreation(null)
      if (created === undefined) {
        setMessage('Sân chưa được tạo. Bạn có thể kiểm tra thông tin và thử lại.')
        return false
      }
      recoveryCourtRef.current = created
      setRecoveryCourt(created)
      setMessage(
        'Sân đã được tạo nhưng chưa thêm vào phiên. Bạn có thể chọn sân này và thử thêm lại.',
      )
      return true
    } catch {
      setMessage(
        'Vẫn chưa thể kiểm tra danh sách sân. Chưa gửi lại yêu cầu tạo sân.',
      )
      return false
    } finally {
      inFlight.current = false
    }
  }, [queryClient, unknownCreation])

  return {
    addCourt: (court: CourtResponse) =>
      execute({ type: 'existing', court }),
    createAndAddCourt: (
      venueId: string,
      request: CreateCourtRequest,
    ) => execute({ type: 'create', venueId, request }),
    retryCreatedCourt: () => {
      const court = recoveryCourtRef.current
      return court === null
        ? Promise.resolve(false)
        : execute({ type: 'retry-created', court })
    },
    reconcileUnknown,
    reconcileUnknownCreation,
    isPending: mutation.isPending,
    pendingStage,
    recoveryCourt,
    hasUnknownOutcome: unknownCourtId !== null,
    hasUnknownCreateOutcome: unknownCreation !== null,
    message,
  }
}
