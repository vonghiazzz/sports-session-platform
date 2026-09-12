import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef, useState } from 'react'
import type {
  PlayerResponse,
  SkillLevel,
  SportCode,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  getPlayer,
  getPlayers,
  updatePlayerSkillLevel,
} from '../../api/playerApi'
import { badmintonProfile } from './playerManagementModel'

export const playerQueryKeys = {
  all: ['players'] as const,
  list: (search: string) => ['players', search] as const,
  detail: (playerId: string) => ['player', playerId] as const,
}

export function usePlayerList(search: string) {
  return useQuery({
    queryKey: playerQueryKeys.list(search),
    queryFn: ({ signal }) => getPlayers(search, signal),
    retry: false,
  })
}

export function usePlayerDetail(playerId: string) {
  return useQuery({
    queryKey: playerQueryKeys.detail(playerId),
    queryFn: ({ signal }) => getPlayer(playerId, signal),
    enabled: playerId.length > 0,
    retry: false,
  })
}

function knownFailureMessage(error: HttpError): string {
  if (error.status === 404) {
    return 'Không tìm thấy người chơi hoặc hồ sơ môn thể thao.'
  }
  if (error.status === 400) {
    return 'Trình độ đã chọn không hợp lệ.'
  }
  return 'Không thể cập nhật trình độ. Dữ liệu người chơi vẫn được giữ nguyên.'
}

export function usePlayerSkillLevelUpdate(playerId: string) {
  const queryClient = useQueryClient()
  const inFlight = useRef(false)
  const unresolvedRequest = useRef<{
    readonly sportCode: SportCode
    readonly skillLevel: SkillLevel
  } | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [outcomeUnknown, setOutcomeUnknown] = useState(false)
  const [isReconciling, setIsReconciling] = useState(false)

  const mutation = useMutation({
    mutationKey: ['updatePlayerSkillLevel', playerId],
    mutationFn: ({
      sportCode,
      skillLevel,
    }: {
      readonly sportCode: SportCode
      readonly skillLevel: SkillLevel
    }) => updatePlayerSkillLevel(playerId, sportCode, { skillLevel }),
    retry: false,
  })

  const acceptAuthoritativePlayer = useCallback(
    (player: PlayerResponse) => {
      queryClient.setQueryData(playerQueryKeys.detail(playerId), player)
      void queryClient.invalidateQueries({ queryKey: playerQueryKeys.all })
    },
    [playerId, queryClient],
  )

  const reconcile = useCallback(
    async (
      request: {
        readonly sportCode: SportCode
        readonly skillLevel: SkillLevel
      },
    ): Promise<boolean> => {
      setIsReconciling(true)
      try {
        const player = await getPlayer(playerId)
        acceptAuthoritativePlayer(player)
        setOutcomeUnknown(false)
        unresolvedRequest.current = null
        if (badmintonProfile(player.sportProfiles)?.skillLevel === request.skillLevel) {
          setErrorMessage(null)
          setSuccessMessage('Đã xác nhận trình độ mới từ dữ liệu hệ thống.')
        } else {
          setSuccessMessage(null)
          setErrorMessage(
            'Hệ thống chưa áp dụng trình độ đã chọn. Bạn có thể kiểm tra và gửi lại.',
          )
        }
        return true
      } catch {
        setOutcomeUnknown(true)
        unresolvedRequest.current = request
        setSuccessMessage(null)
        setErrorMessage(
          'Mất kết nối nên chưa xác định được trình độ đã cập nhật hay chưa. Hãy kiểm tra lại trước khi gửi lần nữa.',
        )
        return false
      } finally {
        setIsReconciling(false)
      }
    },
    [acceptAuthoritativePlayer, playerId],
  )

  const update = useCallback(
    async (sportCode: SportCode, skillLevel: SkillLevel) => {
      if (inFlight.current || outcomeUnknown) {
        return
      }
      inFlight.current = true
      setErrorMessage(null)
      setSuccessMessage(null)
      const request = { sportCode, skillLevel }
      try {
        const player = await mutation.mutateAsync(request)
        acceptAuthoritativePlayer(player)
        setSuccessMessage('Đã cập nhật trình độ người chơi.')
      } catch (error) {
        if (error instanceof HttpError) {
          setErrorMessage(knownFailureMessage(error))
        } else {
          await reconcile(request)
        }
      } finally {
        inFlight.current = false
      }
    },
    [acceptAuthoritativePlayer, mutation, outcomeUnknown, reconcile],
  )

  const checkUnknownOutcome = useCallback(async () => {
    const request = unresolvedRequest.current
    if (request === null || inFlight.current) {
      return
    }
    inFlight.current = true
    try {
      await reconcile(request)
    } finally {
      inFlight.current = false
    }
  }, [reconcile])

  return {
    update,
    checkUnknownOutcome,
    isPending: mutation.isPending || isReconciling,
    outcomeUnknown,
    errorMessage,
    successMessage,
    clearFeedback: () => {
      if (!outcomeUnknown) {
        setErrorMessage(null)
        setSuccessMessage(null)
      }
    },
  }
}
