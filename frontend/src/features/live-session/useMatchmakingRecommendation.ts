import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef, useState } from 'react'
import type {
  AcceptMatchmakingRecommendationRequest,
  MatchRecommendationResponse,
  MatchResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  acceptMatchmakingRecommendation,
  generateMatchmakingRecommendation,
} from '../../api/matchmakingApi'

interface MatchmakingRecommendationState {
  readonly recommendation: MatchRecommendationResponse | null
  readonly generate: () => Promise<void>
  readonly accept: () => Promise<void>
  readonly dismiss: () => void
  readonly isGenerating: boolean
  readonly isAccepting: boolean
  readonly acceptBlocked: boolean
  readonly generateError: string | null
  readonly acceptError: string | null
}

function recommendationRequest(
  recommendation: MatchRecommendationResponse,
): AcceptMatchmakingRecommendationRequest {
  const players = [
    recommendation.teamA.slot1,
    recommendation.teamA.slot2,
    recommendation.teamB.slot1,
    recommendation.teamB.slot2,
  ]
  return {
    algorithmVersion: recommendation.algorithmVersion,
    assignments: players.map((player) => ({
      sessionParticipantId: player.sessionParticipantId,
      teamSide: player.teamSide,
      teamSlot: player.teamSlot,
    })),
  }
}

function generatedAssignments(
  recommendation: MatchRecommendationResponse,
): readonly string[] {
  return recommendationRequest(recommendation).assignments
    .map(
      (assignment) =>
        `${assignment.sessionParticipantId}:${assignment.teamSide}:${assignment.teamSlot}`,
    )
    .toSorted()
}

function isAcceptedRecommendation(
  match: MatchResponse,
  recommendation: MatchRecommendationResponse,
): boolean {
  if (
    match.sessionCourtId !== recommendation.sessionCourtId ||
    match.source !== 'RECOMMENDATION' ||
    match.status !== 'PLAYING'
  ) {
    return false
  }
  const actualAssignments = match.participants
    .map(
      (assignment) =>
        `${assignment.sessionParticipantId}:${assignment.teamSide}:${assignment.teamSlot}`,
    )
    .toSorted()
  return (
    actualAssignments.length === 4 &&
    actualAssignments.every(
      (assignment, index) =>
        assignment === generatedAssignments(recommendation)[index],
    )
  )
}

function generateFailureMessage(error: unknown): string {
  if (!(error instanceof HttpError)) {
    return 'Mất kết nối khi tạo đề xuất. Bạn có thể chủ động tạo lại; thao tác này chưa giữ sân hoặc người chơi.'
  }
  if (error.status === 409) {
    return 'Không thể tạo đề xuất vì trạng thái phiên hoặc sân đã thay đổi.'
  }
  if (error.status === 404) {
    return 'Phiên hoặc sân không còn khả dụng để tạo đề xuất.'
  }
  return 'Không thể tạo đề xuất lúc này. Hãy thử lại sau.'
}

const runtimeQueryNames = [
  'session',
  'sessionMatches',
  'sessionParticipants',
  'sessionCourts',
] as const

export function useMatchmakingRecommendation(
  sessionId: string,
  sessionCourtId: string,
): MatchmakingRecommendationState {
  const queryClient = useQueryClient()
  const generateInFlight = useRef(false)
  const acceptInFlight = useRef(false)
  const [recommendation, setRecommendation] =
    useState<MatchRecommendationResponse | null>(null)
  const [generateError, setGenerateError] = useState<string | null>(null)
  const [acceptError, setAcceptError] = useState<string | null>(null)
  const [acceptBlocked, setAcceptBlocked] = useState(false)

  const generateMutation = useMutation({
    mutationKey: [
      'generateMatchmakingRecommendation',
      sessionId,
      sessionCourtId,
    ],
    mutationFn: () =>
      generateMatchmakingRecommendation(sessionId, sessionCourtId),
    retry: false,
  })
  const acceptMutation = useMutation({
    mutationKey: [
      'acceptMatchmakingRecommendation',
      sessionId,
      sessionCourtId,
    ],
    mutationFn: (request: AcceptMatchmakingRecommendationRequest) =>
      acceptMatchmakingRecommendation(sessionId, sessionCourtId, request),
    retry: false,
  })

  const reconcileRuntime = useCallback(async () => {
    await Promise.all(
      runtimeQueryNames.map((queryName) =>
        queryClient.refetchQueries(
          { queryKey: [queryName, sessionId], exact: true, type: 'active' },
          { throwOnError: true },
        ),
      ),
    )
  }, [queryClient, sessionId])

  const generate = useCallback(async () => {
    if (generateInFlight.current) {
      return
    }
    generateInFlight.current = true
    setGenerateError(null)
    try {
      const result = await generateMutation.mutateAsync()
      if (result.outcome === 'UNAVAILABLE') {
        setRecommendation(null)
        setAcceptError(null)
        setAcceptBlocked(false)
        setGenerateError(
          'Chưa đủ bốn người chơi đang chờ đủ điều kiện để tạo đề xuất.',
        )
        return
      }
      setRecommendation(result)
      setAcceptError(null)
      setAcceptBlocked(false)
    } catch (error) {
      setGenerateError(generateFailureMessage(error))
    } finally {
      generateInFlight.current = false
    }
  }, [generateMutation])

  const accept = useCallback(async () => {
    if (
      recommendation === null ||
      acceptInFlight.current ||
      acceptBlocked
    ) {
      return
    }
    acceptInFlight.current = true
    setAcceptError(null)
    try {
      await acceptMutation.mutateAsync(recommendationRequest(recommendation))
    } catch (error) {
      let reconciled = false
      try {
        await reconcileRuntime()
        reconciled = true
      } catch {
        // Scoped feedback below preserves the unknown server outcome.
      }

      const matches =
        queryClient.getQueryData<readonly MatchResponse[]>([
          'sessionMatches',
          sessionId,
        ]) ?? []
      if (
        reconciled &&
        matches.some((match) =>
          isAcceptedRecommendation(match, recommendation),
        )
      ) {
        setRecommendation(null)
        setAcceptBlocked(false)
        return
      }

      setAcceptBlocked(true)
      if (error instanceof HttpError && error.status === 409) {
        setAcceptError(
          'Đề xuất không còn hợp lệ vì trạng thái phiên đã thay đổi. Hãy tạo đề xuất mới hoặc tạo trận thủ công.',
        )
      } else if (!(error instanceof HttpError)) {
        setAcceptError(
          reconciled
            ? 'Không xác định được phản hồi chấp nhận. Dữ liệu hiện tại đã được kiểm tra; hãy tạo đề xuất mới trước khi thử tiếp.'
            : 'Không xác định được đề xuất đã được chấp nhận hay chưa. Hãy dùng “Làm mới” để kiểm tra; không chấp nhận lại đề xuất này.',
        )
      } else {
        setAcceptError(
          'Không thể chấp nhận đề xuất. Dữ liệu trực tiếp đã được tải lại; hãy tạo đề xuất mới.',
        )
      }
      return
    } finally {
      acceptInFlight.current = false
    }

    setRecommendation(null)
    setAcceptBlocked(false)
    try {
      await reconcileRuntime()
    } catch {
      setAcceptError(
        'Trận đã được bắt đầu nhưng chưa thể tải lại toàn bộ dữ liệu. Hãy dùng “Làm mới” để đồng bộ.',
      )
    }
  }, [
    acceptBlocked,
    acceptMutation,
    queryClient,
    recommendation,
    reconcileRuntime,
    sessionId,
  ])

  const dismiss = useCallback(() => {
    setRecommendation(null)
    setGenerateError(null)
    setAcceptError(null)
    setAcceptBlocked(false)
  }, [])

  return {
    recommendation,
    generate,
    accept,
    dismiss,
    isGenerating: generateMutation.isPending,
    isAccepting: acceptMutation.isPending,
    acceptBlocked,
    generateError,
    acceptError,
  }
}
