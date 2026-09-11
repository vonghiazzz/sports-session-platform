import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef, useState } from 'react'
import type {
  GlobalMatchmakingGenerationResponse,
  GlobalMatchmakingQueueRequest,
  MatchPlanResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  generateGlobalMatchmakingPreview,
  queueGlobalMatchmakingRecommendations,
} from '../../api/matchmakingApi'
import {
  buildGlobalMatchmakingQueueRequest,
  reconcileGlobalQueueOutcome,
} from './globalMatchmakingQueue'

type GlobalQueueStatus =
  | 'IDLE'
  | 'QUEUEING'
  | 'STALE'
  | 'RECONCILING'
  | 'UNKNOWN'

interface GlobalMatchmakingRecommendationState {
  readonly preview: GlobalMatchmakingGenerationResponse | null
  readonly generate: () => Promise<void>
  readonly queueAll: () => Promise<void>
  readonly checkQueueOutcome: () => Promise<void>
  readonly dismiss: () => void
  readonly isGenerating: boolean
  readonly isQueueing: boolean
  readonly isReconciling: boolean
  readonly isStale: boolean
  readonly hasUnknownOutcome: boolean
  readonly canQueue: boolean
  readonly canRegenerate: boolean
  readonly canDismiss: boolean
  readonly generateError: string | null
  readonly queueMessage: string | null
  readonly successMessage: string | null
}

function generateFailureMessage(error: unknown): string {
  if (!(error instanceof HttpError)) {
    return 'Mất kết nối khi tạo đề xuất toàn phiên. Đề xuất trước đó, nếu có, vẫn được giữ lại.'
  }
  if (error.status === 409) {
    return 'Không thể tạo đề xuất toàn phiên vì trạng thái phiên đã thay đổi.'
  }
  if (error.status === 404) {
    return 'Phiên không còn khả dụng để tạo đề xuất toàn phiên.'
  }
  return 'Không thể tạo đề xuất toàn phiên lúc này. Hãy thử lại sau.'
}

function knownQueueFailureMessage(error: HttpError): string {
  if (error.status === 409) {
    return 'Đề xuất đã thay đổi theo trạng thái mới của phiên. Hãy tạo lại trước khi thêm vào hàng chờ.'
  }
  if (error.status === 404) {
    return 'Phiên hoặc tài nguyên liên quan không còn khả dụng. Hãy làm mới dữ liệu.'
  }
  if (error.status === 400) {
    return 'Bằng chứng đề xuất không còn hợp lệ. Hãy tạo lại trước khi thêm vào hàng chờ.'
  }
  return 'Không thể thêm đề xuất vào hàng chờ. Hãy tạo lại trước khi thử tiếp.'
}

const UNKNOWN_OUTCOME_MESSAGE =
  'Chưa xác định được yêu cầu đã được ghi nhận hay chưa. Hãy kiểm tra lại trạng thái hàng chờ trước khi thử lại.'

export function useGlobalMatchmakingRecommendation(
  sessionId: string,
): GlobalMatchmakingRecommendationState {
  const queryClient = useQueryClient()
  const generateInFlight = useRef(false)
  const queueInFlight = useRef(false)
  const submittedRequest = useRef<{
    readonly sessionId: string
    readonly request: GlobalMatchmakingQueueRequest
  } | null>(null)
  const [previewState, setPreviewState] = useState<{
    readonly sessionId: string
    readonly value: GlobalMatchmakingGenerationResponse
  } | null>(null)
  const [generateErrorState, setGenerateErrorState] = useState<{
    readonly sessionId: string
    readonly message: string
  } | null>(null)
  const [queueState, setQueueState] = useState<{
    readonly sessionId: string
    readonly status: GlobalQueueStatus
    readonly message: string | null
  } | null>(null)
  const [successState, setSuccessState] = useState<{
    readonly sessionId: string
    readonly message: string
  } | null>(null)

  const preview =
    previewState?.sessionId === sessionId ? previewState.value : null
  const currentQueueState =
    queueState?.sessionId === sessionId
      ? queueState
      : { sessionId, status: 'IDLE' as const, message: null }

  const generateMutation = useMutation({
    mutationKey: ['generateGlobalMatchmakingPreview', sessionId],
    mutationFn: () => generateGlobalMatchmakingPreview(sessionId),
    retry: false,
  })
  const queueMutation = useMutation({
    mutationKey: ['queueGlobalMatchmakingRecommendations', sessionId],
    mutationFn: (request: GlobalMatchmakingQueueRequest) =>
      queueGlobalMatchmakingRecommendations(sessionId, request),
    retry: false,
  })

  const refreshMatchPlans = useCallback(async () => {
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

  const completeQueueSuccess = useCallback(
    (message: string) => {
      setPreviewState((current) =>
        current?.sessionId === sessionId ? null : current,
      )
      setQueueState({ sessionId, status: 'IDLE', message: null })
      setSuccessState({ sessionId, message })
      submittedRequest.current = null
    },
    [sessionId],
  )

  const reconcileSubmittedRequest = useCallback(async () => {
    const submitted = submittedRequest.current
    if (submitted === null || submitted.sessionId !== sessionId) {
      return false
    }
    const plans = await refreshMatchPlans()
    return (
      reconcileGlobalQueueOutcome(
        sessionId,
        submitted.request.recommendations,
        plans,
      ) === 'CONFIRMED'
    )
  }, [refreshMatchPlans, sessionId])

  const enterUnknownState = useCallback(() => {
    setQueueState({
      sessionId,
      status: 'UNKNOWN',
      message: UNKNOWN_OUTCOME_MESSAGE,
    })
  }, [sessionId])

  const generate = useCallback(async () => {
    if (
      generateInFlight.current ||
      currentQueueState.status === 'QUEUEING' ||
      currentQueueState.status === 'RECONCILING' ||
      currentQueueState.status === 'UNKNOWN'
    ) {
      return
    }

    generateInFlight.current = true
    setGenerateErrorState(null)
    setSuccessState(null)
    try {
      const result = await generateMutation.mutateAsync()
      setPreviewState({ sessionId, value: result })
      setQueueState({ sessionId, status: 'IDLE', message: null })
      submittedRequest.current = null
    } catch (error) {
      setGenerateErrorState({
        sessionId,
        message: generateFailureMessage(error),
      })
    } finally {
      generateInFlight.current = false
    }
  }, [currentQueueState.status, generateMutation, sessionId])

  const queueAll = useCallback(async () => {
    if (
      preview === null ||
      queueInFlight.current ||
      currentQueueState.status !== 'IDLE'
    ) {
      return
    }
    const request = buildGlobalMatchmakingQueueRequest(preview)
    if (request.recommendations.length === 0) {
      return
    }

    queueInFlight.current = true
    submittedRequest.current = { sessionId, request }
    setQueueState({ sessionId, status: 'QUEUEING', message: null })
    setGenerateErrorState(null)
    setSuccessState(null)
    try {
      const response = await queueMutation.mutateAsync(request)
      const responseIsComplete =
        reconcileGlobalQueueOutcome(
          sessionId,
          request.recommendations,
          response.createdPlans,
        ) === 'CONFIRMED'

      if (responseIsComplete) {
        queryClient.setQueryData<readonly MatchPlanResponse[]>(
          ['sessionMatchPlans', sessionId],
          (current = []) => {
            const createdIds = new Set(
              response.createdPlans.map((plan) => plan.id),
            )
            return [
              ...current.filter((plan) => !createdIds.has(plan.id)),
              ...response.createdPlans,
            ]
          },
        )
        try {
          await refreshMatchPlans()
          completeQueueSuccess('Đã thêm tất cả đề xuất vào hàng chờ.')
        } catch {
          completeQueueSuccess(
            'Đã thêm tất cả đề xuất vào hàng chờ nhưng chưa thể tải lại dữ liệu mới nhất. Bạn có thể dùng “Làm mới”.',
          )
        }
        return
      }

      setQueueState({ sessionId, status: 'RECONCILING', message: null })
      try {
        if (await reconcileSubmittedRequest()) {
          completeQueueSuccess('Đã thêm tất cả đề xuất vào hàng chờ.')
        } else {
          enterUnknownState()
        }
      } catch {
        enterUnknownState()
      }
    } catch (error) {
      if (error instanceof HttpError) {
        submittedRequest.current = null
        setQueueState({
          sessionId,
          status: 'STALE',
          message: knownQueueFailureMessage(error),
        })
        return
      }

      setQueueState({ sessionId, status: 'RECONCILING', message: null })
      try {
        if (await reconcileSubmittedRequest()) {
          completeQueueSuccess('Đã xác nhận các đề xuất có trong hàng chờ.')
        } else {
          enterUnknownState()
        }
      } catch {
        enterUnknownState()
      }
    } finally {
      queueInFlight.current = false
    }
  }, [
    completeQueueSuccess,
    currentQueueState.status,
    enterUnknownState,
    preview,
    queryClient,
    queueMutation,
    reconcileSubmittedRequest,
    refreshMatchPlans,
    sessionId,
  ])

  const checkQueueOutcome = useCallback(async () => {
    if (
      queueInFlight.current ||
      currentQueueState.status !== 'UNKNOWN'
    ) {
      return
    }
    queueInFlight.current = true
    setQueueState({ sessionId, status: 'RECONCILING', message: null })
    try {
      if (await reconcileSubmittedRequest()) {
        completeQueueSuccess('Đã xác nhận các đề xuất có trong hàng chờ.')
      } else {
        enterUnknownState()
      }
    } catch {
      enterUnknownState()
    } finally {
      queueInFlight.current = false
    }
  }, [
    completeQueueSuccess,
    currentQueueState.status,
    enterUnknownState,
    reconcileSubmittedRequest,
    sessionId,
  ])

  const dismiss = useCallback(() => {
    if (
      currentQueueState.status === 'QUEUEING' ||
      currentQueueState.status === 'RECONCILING' ||
      currentQueueState.status === 'UNKNOWN'
    ) {
      return
    }
    setPreviewState((current) =>
      current?.sessionId === sessionId ? null : current,
    )
    setGenerateErrorState(null)
    setQueueState({ sessionId, status: 'IDLE', message: null })
    setSuccessState(null)
    submittedRequest.current = null
  }, [currentQueueState.status, sessionId])

  const hasRecommendations =
    preview?.courtResults.some((result) => result.outcome === 'RECOMMENDED') ??
    false
  const busy =
    currentQueueState.status === 'QUEUEING' ||
    currentQueueState.status === 'RECONCILING'

  return {
    preview,
    generate,
    queueAll,
    checkQueueOutcome,
    dismiss,
    isGenerating: generateMutation.isPending,
    isQueueing: currentQueueState.status === 'QUEUEING',
    isReconciling: currentQueueState.status === 'RECONCILING',
    isStale: currentQueueState.status === 'STALE',
    hasUnknownOutcome: currentQueueState.status === 'UNKNOWN',
    canQueue:
      preview !== null &&
      hasRecommendations &&
      currentQueueState.status === 'IDLE' &&
      !generateMutation.isPending,
    canRegenerate:
      !busy &&
      currentQueueState.status !== 'UNKNOWN' &&
      !generateMutation.isPending,
    canDismiss:
      !busy &&
      currentQueueState.status !== 'UNKNOWN' &&
      !generateMutation.isPending,
    generateError:
      generateErrorState?.sessionId === sessionId
        ? generateErrorState.message
        : null,
    queueMessage: currentQueueState.message,
    successMessage:
      successState?.sessionId === sessionId ? successState.message : null,
  }
}
