import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef, useState } from 'react'
import type {
  MatchPlanResponse,
  SaveMatchPlanRequest,
  StartedMatchPlanResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  cancelMatchPlan,
  createMatchPlan,
  moveMatchPlan,
  reorderMatchPlan,
  startMatchPlan,
  updateMatchPlan,
} from '../../api/matchPlanApi'

const START_RUNTIME_QUERY_NAMES = [
  'session',
  'sessionMatchPlans',
  'sessionMatches',
  'sessionParticipants',
  'sessionCourts',
] as const

export type MatchPlanAction =
  | { readonly type: 'EDIT'; readonly request: SaveMatchPlanRequest }
  | { readonly type: 'MOVE'; readonly targetSessionCourtId: string }
  | { readonly type: 'REORDER'; readonly targetPosition: number }
  | { readonly type: 'CANCEL' }
  | { readonly type: 'START' }

interface MatchPlanActionState {
  readonly execute: (action: MatchPlanAction) => Promise<boolean>
  readonly reconcileUnknown: () => Promise<void>
  readonly isPending: boolean
  readonly pendingAction: MatchPlanAction['type'] | null
  readonly errorMessage: string | null
  readonly hasUnknownOutcome: boolean
}

interface CreateMatchPlanState {
  readonly execute: (request: SaveMatchPlanRequest) => Promise<boolean>
  readonly reconcileUnknown: () => Promise<void>
  readonly isPending: boolean
  readonly errorMessage: string | null
  readonly hasUnknownOutcome: boolean
}

function knownFailureMessage(error: HttpError, action: MatchPlanAction['type']) {
  if (error.status === 400) {
    return 'Hãy kiểm tra đủ bốn vị trí người chơi rồi thử lại.'
  }
  if (error.status === 404) {
    return 'Trận chờ hoặc tài nguyên liên quan không còn khả dụng.'
  }
  if (error.status === 409) {
    return action === 'START'
      ? 'Không thể bắt đầu trận vì trạng thái sân hoặc người chơi đã thay đổi.'
      : 'Hàng chờ đã thay đổi. Dữ liệu mới nhất đã được tải lại.'
  }
  return 'Không thể hoàn tất thao tác với hàng chờ trận.'
}

export function useCreateMatchPlan(
  sessionId: string,
  sessionCourtId: string,
): CreateMatchPlanState {
  const queryClient = useQueryClient()
  const inFlight = useRef(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [hasUnknownOutcome, setHasUnknownOutcome] = useState(false)
  const mutation = useMutation<MatchPlanResponse, Error, SaveMatchPlanRequest>({
    mutationKey: ['createMatchPlan', sessionId, sessionCourtId],
    mutationFn: (request) => createMatchPlan(sessionId, sessionCourtId, request),
    retry: false,
  })

  const reconcile = useCallback(async () => {
    await queryClient.refetchQueries(
      {
        queryKey: ['sessionMatchPlans', sessionId],
        exact: true,
        type: 'active',
      },
      { throwOnError: true },
    )
  }, [queryClient, sessionId])

  const execute = useCallback(
    async (request: SaveMatchPlanRequest) => {
      if (inFlight.current || hasUnknownOutcome) {
        return false
      }
      inFlight.current = true
      setErrorMessage(null)
      try {
        await mutation.mutateAsync(request)
        await reconcile()
        return true
      } catch (error) {
        try {
          await reconcile()
        } catch {
          // Feedback below truthfully preserves the uncertain result.
        }
        if (error instanceof HttpError) {
          setErrorMessage(knownFailureMessage(error, 'EDIT'))
        } else {
          setHasUnknownOutcome(true)
          setErrorMessage(
            'Chưa xác định được trận chờ đã được tạo hay chưa. Hãy kiểm tra lại trước khi tạo thêm để tránh trùng.',
          )
        }
        return false
      } finally {
        inFlight.current = false
      }
    },
    [hasUnknownOutcome, mutation, reconcile],
  )

  const reconcileUnknown = useCallback(async () => {
    try {
      await reconcile()
      setHasUnknownOutcome(false)
      setErrorMessage(null)
    } catch {
      setErrorMessage(
        'Chưa thể kiểm tra hàng chờ. Không nên gửi lại thao tác tạo lúc này.',
      )
    }
  }, [reconcile])

  return {
    execute,
    reconcileUnknown,
    isPending: mutation.isPending,
    errorMessage,
    hasUnknownOutcome,
  }
}

export function useMatchPlanActions(
  sessionId: string,
  matchPlanId: string,
): MatchPlanActionState {
  const queryClient = useQueryClient()
  const inFlight = useRef(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [hasUnknownOutcome, setHasUnknownOutcome] = useState(false)
  const mutation = useMutation<
    MatchPlanResponse | StartedMatchPlanResponse,
    Error,
    MatchPlanAction
  >({
    mutationKey: ['matchPlanAction', matchPlanId],
    mutationFn: (action) => {
      switch (action.type) {
        case 'EDIT':
          return updateMatchPlan(matchPlanId, action.request)
        case 'MOVE':
          return moveMatchPlan(matchPlanId, {
            targetSessionCourtId: action.targetSessionCourtId,
          })
        case 'REORDER':
          return reorderMatchPlan(matchPlanId, {
            targetPosition: action.targetPosition,
          })
        case 'CANCEL':
          return cancelMatchPlan(matchPlanId)
        case 'START':
          return startMatchPlan(matchPlanId)
      }
    },
    retry: false,
  })

  const reconcile = useCallback(
    async (action: MatchPlanAction['type']) => {
      const queryNames =
        action === 'START'
          ? START_RUNTIME_QUERY_NAMES
          : (['sessionMatchPlans'] as const)
      await Promise.all(
        queryNames.map((queryName) =>
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
    },
    [queryClient, sessionId],
  )

  const execute = useCallback(
    async (action: MatchPlanAction) => {
      if (inFlight.current || hasUnknownOutcome) {
        return false
      }
      inFlight.current = true
      setErrorMessage(null)
      try {
        await mutation.mutateAsync(action)
        await reconcile(action.type)
        return true
      } catch (error) {
        let reconciled = false
        try {
          await reconcile(action.type)
          reconciled = true
        } catch {
          // Feedback below truthfully preserves the uncertain result.
        }

        if (error instanceof HttpError) {
          setErrorMessage(knownFailureMessage(error, action.type))
        } else if (reconciled) {
          setErrorMessage(
            action.type === 'START'
              ? 'Phản hồi bị gián đoạn. Trạng thái sân, người chơi, trận và hàng chờ mới nhất đã được kiểm tra.'
              : 'Phản hồi bị gián đoạn. Hàng chờ mới nhất đã được tải lại.',
          )
        } else {
          setHasUnknownOutcome(true)
          setErrorMessage(
            'Chưa xác định được kết quả thao tác. Hãy kiểm tra lại trước khi thử tiếp.',
          )
        }
        return false
      } finally {
        inFlight.current = false
      }
    },
    [hasUnknownOutcome, mutation, reconcile],
  )

  const reconcileUnknown = useCallback(async () => {
    try {
      await reconcile('START')
      setHasUnknownOutcome(false)
      setErrorMessage(null)
    } catch {
      setErrorMessage(
        'Chưa thể kiểm tra trạng thái mới nhất. Không nên lặp lại thao tác lúc này.',
      )
    }
  }, [reconcile])

  return {
    execute,
    reconcileUnknown,
    isPending: mutation.isPending,
    pendingAction: mutation.isPending ? mutation.variables.type : null,
    errorMessage,
    hasUnknownOutcome,
  }
}
