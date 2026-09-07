import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef, useState } from 'react'
import type {
  AcceptMatchmakingRecommendationRequest,
  MatchPlanResponse,
  MatchRecommendationResponse,
  MatchResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  acceptMatchmakingRecommendation,
  generateMatchmakingRecommendation,
  queueMatchmakingRecommendation,
} from '../../api/matchmakingApi'

interface MatchmakingRecommendationState {
  readonly recommendation: MatchRecommendationResponse | null

  readonly generate: () => Promise<void>
  readonly accept: () => Promise<void>
  readonly addToQueue: () => Promise<void>
  readonly checkQueueOutcome: () => Promise<void>
  readonly dismiss: () => void

  readonly isGenerating: boolean
  readonly isAccepting: boolean
  readonly isQueueing: boolean
  readonly isCheckingQueue: boolean

  readonly acceptBlocked: boolean
  readonly queueBlocked: boolean

  readonly generateError: string | null
  readonly acceptError: string | null
  readonly queueError: string | null
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

function planAssignments(plan: MatchPlanResponse): readonly string[] {
  return plan.participants
    .map(
      (assignment) =>
        `${assignment.sessionParticipantId}:${assignment.teamSide}:${assignment.teamSlot}`,
    )
    .toSorted()
}

function sameAssignments(
  actual: readonly string[],
  expected: readonly string[],
): boolean {
  return (
    actual.length === expected.length &&
    actual.every((assignment, index) => assignment === expected[index])
  )
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

  return sameAssignments(
    match.participants
      .map(
        (assignment) =>
          `${assignment.sessionParticipantId}:${assignment.teamSide}:${assignment.teamSlot}`,
      )
      .toSorted(),
    generatedAssignments(recommendation),
  )
}

function isQueuedRecommendation(
  plan: MatchPlanResponse,
  recommendation: MatchRecommendationResponse,
): boolean {
  if (
    plan.sessionCourtId !== recommendation.sessionCourtId ||
    plan.source !== 'RECOMMENDATION'
  ) {
    return false
  }

  return sameAssignments(
    planAssignments(plan),
    generatedAssignments(recommendation),
  )
}

function generateFailureMessage(error: unknown): string {
  if (!(error instanceof HttpError)) {
    return 'Mất kết nối khi tạo đề xuất. Bạn có thể chủ động tạo lại; thao tác này chưa giữ sân hoặc người chơi.'
  }

  if (error.status === 409) {
    return 'Không thể tạo đề xuất vì trạng thái phiên đã thay đổi.'
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
  const queueInFlight = useRef(false)

  const queueAttemptExistingPlanIds = useRef<ReadonlySet<string> | null>(
    null,
  )

  const [recommendation, setRecommendation] =
    useState<MatchRecommendationResponse | null>(null)

  const [generateError, setGenerateError] = useState<string | null>(null)
  const [acceptError, setAcceptError] = useState<string | null>(null)
  const [queueError, setQueueError] = useState<string | null>(null)

  const [acceptBlocked, setAcceptBlocked] = useState(false)
  const [queueBlocked, setQueueBlocked] = useState(false)
  const [isCheckingQueue, setIsCheckingQueue] = useState(false)

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

  const queueMutation = useMutation({
    mutationKey: [
      'queueMatchmakingRecommendation',
      sessionId,
      sessionCourtId,
    ],
    mutationFn: (request: AcceptMatchmakingRecommendationRequest) =>
      queueMatchmakingRecommendation(sessionId, sessionCourtId, request),
    retry: false,
  })

  const reconcileRuntime = useCallback(async () => {
    await Promise.all(
      runtimeQueryNames.map((queryName) =>
        queryClient.refetchQueries(
          {
            queryKey: [queryName, sessionId],
            exact: true,
            type: 'active',
          },
          { throwOnError: true },
        ),
      ),
    )
  }, [queryClient, sessionId])

  const reconcileMatchPlans = useCallback(async () => {
    await queryClient.refetchQueries(
      {
        queryKey: ['sessionMatchPlans', sessionId],
        exact: true,
        type: 'active',
      },
      { throwOnError: true },
    )

    return (
      queryClient.getQueryData<readonly MatchPlanResponse[]>([
        'sessionMatchPlans',
        sessionId,
      ]) ?? []
    )
  }, [queryClient, sessionId])

  const generate = useCallback(async () => {
    if (generateInFlight.current || queueBlocked) {
      return
    }

    generateInFlight.current = true

    setGenerateError(null)
    setAcceptError(null)
    setQueueError(null)

    try {
      const result = await generateMutation.mutateAsync()

      if (result.outcome === 'UNAVAILABLE') {
        setRecommendation(null)
        setAcceptBlocked(false)
        setQueueBlocked(false)

        setGenerateError(
          'Chưa đủ bốn người chơi đang chờ đủ điều kiện để tạo đề xuất.',
        )
        return
      }

      setRecommendation(result)

      setAcceptBlocked(false)
      setQueueBlocked(false)

      queueAttemptExistingPlanIds.current = null
    } catch (error) {
      setGenerateError(generateFailureMessage(error))
    } finally {
      generateInFlight.current = false
    }
  }, [generateMutation, queueBlocked])

  const accept = useCallback(async () => {
    if (
      recommendation === null ||
      acceptInFlight.current ||
      acceptBlocked ||
      queueBlocked
    ) {
      return
    }

    acceptInFlight.current = true

    setAcceptError(null)
    setQueueError(null)

    try {
      await acceptMutation.mutateAsync(
        recommendationRequest(recommendation),
      )
    } catch (error) {
      let reconciled = false

      try {
        await reconcileRuntime()
        reconciled = true
      } catch {
        // Keep the outcome explicitly unknown.
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
          'Đề xuất không thể bắt đầu với trạng thái hiện tại. Bạn có thể tạo đề xuất mới hoặc thêm một đề xuất hợp lệ vào hàng chờ.',
        )
      } else if (!(error instanceof HttpError)) {
        setAcceptError(
          reconciled
            ? 'Không xác định được phản hồi chấp nhận. Dữ liệu hiện tại đã được kiểm tra; không chấp nhận lại đề xuất này.'
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
    queueBlocked,
    recommendation,
    reconcileRuntime,
    sessionId,
  ])

  const addToQueue = useCallback(async () => {
    if (
      recommendation === null ||
      queueInFlight.current ||
      queueBlocked
    ) {
      return
    }

    queueInFlight.current = true

    setQueueError(null)

    const existingPlans =
      queryClient.getQueryData<readonly MatchPlanResponse[]>([
        'sessionMatchPlans',
        sessionId,
      ]) ?? []

    const existingPlanIds = new Set(
      existingPlans.map((plan) => plan.id),
    )

    queueAttemptExistingPlanIds.current = existingPlanIds

    try {
      await queueMutation.mutateAsync(
        recommendationRequest(recommendation),
      )
    } catch (error) {
      if (error instanceof HttpError) {
        if (error.status === 409) {
          setRecommendation(null)
          setQueueBlocked(false)
          queueAttemptExistingPlanIds.current = null

          setGenerateError(
            'Đề xuất không còn hợp lệ để thêm vào hàng chờ. Hãy tạo đề xuất mới.',
          )
          return
        }

        if (error.status === 404) {
          setQueueError(
            'Phiên hoặc sân không còn khả dụng để thêm đề xuất vào hàng chờ.',
          )
          return
        }

        setQueueError(
          'Không thể thêm đề xuất vào hàng chờ lúc này. Hãy thử lại sau.',
        )
        return
      }

      try {
        const plans = await reconcileMatchPlans()

        const queuedPlan = plans.find(
          (plan) =>
            !existingPlanIds.has(plan.id) &&
            isQueuedRecommendation(plan, recommendation),
        )

        if (queuedPlan) {
          setRecommendation(null)
          setQueueBlocked(false)
          queueAttemptExistingPlanIds.current = null
          return
        }
      } catch {
        // Outcome remains unknown.
      }

      setQueueBlocked(true)

      setQueueError(
        'Không xác định được đề xuất đã được thêm vào hàng chờ hay chưa. Hãy chọn “Kiểm tra lại” trước khi thử thêm lần nữa.',
      )

      return
    } finally {
      queueInFlight.current = false
    }

    setRecommendation(null)
    setQueueBlocked(false)
    queueAttemptExistingPlanIds.current = null

    try {
      await reconcileMatchPlans()
    } catch {
      setQueueError(
        'Đề xuất đã được thêm vào hàng chờ nhưng chưa thể tải lại danh sách. Hãy dùng “Làm mới” để đồng bộ.',
      )
    }
  }, [
    queryClient,
    queueBlocked,
    queueMutation,
    recommendation,
    reconcileMatchPlans,
    sessionId,
  ])

  const checkQueueOutcome = useCallback(async () => {
    if (
      recommendation === null ||
      !queueBlocked ||
      isCheckingQueue
    ) {
      return
    }

    setIsCheckingQueue(true)
    setQueueError(null)

    try {
      const plans = await reconcileMatchPlans()

      const existingIds =
        queueAttemptExistingPlanIds.current ?? new Set<string>()

      const queuedPlan = plans.find(
        (plan) =>
          !existingIds.has(plan.id) &&
          isQueuedRecommendation(plan, recommendation),
      )

      if (queuedPlan) {
        setRecommendation(null)
        setQueueBlocked(false)
        queueAttemptExistingPlanIds.current = null
        return
      }

      setQueueBlocked(false)
      queueAttemptExistingPlanIds.current = null

      setQueueError(
        'Chưa thấy đề xuất mới trong hàng chờ. Bạn có thể thử thêm lại nếu vẫn muốn dùng đề xuất này.',
      )
    } catch {
      setQueueError(
        'Chưa thể kiểm tra trạng thái hàng chờ. Không nên gửi lại thao tác cho đến khi kiểm tra thành công.',
      )
    } finally {
      setIsCheckingQueue(false)
    }
  }, [
    isCheckingQueue,
    queueBlocked,
    recommendation,
    reconcileMatchPlans,
  ])

  const dismiss = useCallback(() => {
    if (queueBlocked) {
      return
    }

    setRecommendation(null)

    setGenerateError(null)
    setAcceptError(null)
    setQueueError(null)

    setAcceptBlocked(false)
    setQueueBlocked(false)

    queueAttemptExistingPlanIds.current = null
  }, [queueBlocked])

  return {
    recommendation,

    generate,
    accept,
    addToQueue,
    checkQueueOutcome,
    dismiss,

    isGenerating: generateMutation.isPending,
    isAccepting: acceptMutation.isPending,
    isQueueing: queueMutation.isPending,
    isCheckingQueue,

    acceptBlocked,
    queueBlocked,

    generateError,
    acceptError,
    queueError,
  }
}
