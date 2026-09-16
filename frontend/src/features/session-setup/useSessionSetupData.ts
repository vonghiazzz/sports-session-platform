import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef, useState } from 'react'
import type {
  CourtResponse,
  CreateCourtRequest,
  CreatePlayerRequest,
  CreateVenueRequest,
  PlayerResponse,
  VenueResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  createCourt,
  createPlayer,
  createVenue,
  getSetupPlayers,
  getSetupVenueCourts,
  getVenues,
} from '../../api/sessionSetupApi'

type ResourceKind = 'venue' | 'court' | 'player'

interface ResourceIssue {
  readonly kind: ResourceKind
  readonly message: string
  readonly unknownOutcome: boolean
}

function createErrorMessage(kind: ResourceKind, error: unknown): ResourceIssue {
  const labels = {
    venue: 'địa điểm',
    court: 'sân',
    player: 'người chơi',
  } as const
  const label = labels[kind]
  if (!(error instanceof HttpError)) {
    return {
      kind,
      unknownOutcome: true,
      message: `Mất kết nối nên chưa thể xác định ${label} đã được tạo hay chưa. Danh sách đang được tải lại; hãy kiểm tra trước khi nhập dữ liệu khác.`,
    }
  }
  if (error.status === 409) {
    return {
      kind,
      unknownOutcome: false,
      message: `Không thể tạo ${label} vì dữ liệu đã tồn tại hoặc không còn hợp lệ.`,
    }
  }
  return {
    kind,
    unknownOutcome: false,
    message: `Không thể tạo ${label}. Hãy kiểm tra thông tin và thử lại.`,
  }
}

function appendById<T extends { readonly id: string }>(
  current: readonly T[] | undefined,
  created: T,
): readonly T[] {
  if (current?.some((item) => item.id === created.id)) {
    return current
  }
  return [...(current ?? []), created]
}

export function useSessionSetupData(venueId: string) {
  const queryClient = useQueryClient()
  const [issue, setIssue] = useState<ResourceIssue | null>(null)
  const venueGuard = useRef(false)
  const courtGuard = useRef(false)
  const playerGuard = useRef(false)

  const venuesQuery = useQuery({
    queryKey: ['venues'],
    queryFn: ({ signal }) => getVenues(signal),
  })
  const playersQuery = useQuery({
    queryKey: ['players'],
    queryFn: ({ signal }) => getSetupPlayers(signal),
  })
  const courtsQuery = useQuery({
    queryKey: ['venueCourts', venueId],
    queryFn: ({ signal }) => getSetupVenueCourts(venueId, signal),
    enabled: venueId.length > 0,
  })

  const venueMutation = useMutation({
    mutationFn: createVenue,
    retry: false,
  })
  const courtMutation = useMutation({
    mutationFn: ({
      targetVenueId,
      request,
    }: {
      readonly targetVenueId: string
      readonly request: CreateCourtRequest
    }) => createCourt(targetVenueId, request),
    retry: false,
  })
  const playerMutation = useMutation({
    mutationFn: createPlayer,
    retry: false,
  })

  const createSetupVenue = useCallback(
    async (request: CreateVenueRequest): Promise<VenueResponse | null> => {
      if (venueGuard.current) {
        return null
      }
      venueGuard.current = true
      setIssue(null)
      try {
        const created = await venueMutation.mutateAsync(request)
        queryClient.setQueryData<readonly VenueResponse[]>(
          ['venues'],
          (current) => appendById(current, created),
        )
        return created
      } catch (error) {
        setIssue(createErrorMessage('venue', error))
        await queryClient.invalidateQueries({ queryKey: ['venues'], exact: true })
        return null
      } finally {
        venueGuard.current = false
      }
    },
    [queryClient, venueMutation],
  )

  const createSetupCourt = useCallback(
    async (
      targetVenueId: string,
      request: CreateCourtRequest,
    ): Promise<CourtResponse | null> => {
      if (courtGuard.current) {
        return null
      }
      courtGuard.current = true
      setIssue(null)
      try {
        const created = await courtMutation.mutateAsync({
          targetVenueId,
          request,
        })
        queryClient.setQueryData<readonly CourtResponse[]>(
          ['venueCourts', targetVenueId],
          (current) => appendById(current, created),
        )
        return created
      } catch (error) {
        setIssue(createErrorMessage('court', error))
        await queryClient.invalidateQueries({
          queryKey: ['venueCourts', targetVenueId],
          exact: true,
        })
        return null
      } finally {
        courtGuard.current = false
      }
    },
    [courtMutation, queryClient],
  )

  const createSetupPlayer = useCallback(
    async (request: CreatePlayerRequest): Promise<PlayerResponse | null> => {
      if (playerGuard.current) {
        return null
      }
      playerGuard.current = true
      setIssue(null)
      try {
        const created = await playerMutation.mutateAsync(request)
        queryClient.setQueryData<readonly PlayerResponse[]>(
          ['players'],
          (current) => appendById(current, created),
        )
        return created
      } catch (error) {
        setIssue(createErrorMessage('player', error))
        await queryClient.invalidateQueries({ queryKey: ['players'], exact: true })
        return null
      } finally {
        playerGuard.current = false
      }
    },
    [playerMutation, queryClient],
  )

  return {
    venues: venuesQuery.data ?? [],
    players: playersQuery.data ?? [],
    courts: courtsQuery.data ?? [],
    venuesLoading: venuesQuery.isPending,
    playersLoading: playersQuery.isPending,
    courtsLoading: venueId.length > 0 && courtsQuery.isPending,
    venuesError: venuesQuery.isError,
    playersError: playersQuery.isError,
    courtsError: courtsQuery.isError,
    createSetupVenue,
    createSetupCourt,
    createSetupPlayer,
    venueCreationPending: venueMutation.isPending,
    courtCreationPending: courtMutation.isPending,
    playerCreationPending: playerMutation.isPending,
    issue,
    clearIssue: () => setIssue(null),
  }
}
