import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef } from 'react'
import type {
  CompleteMatchRequest,
  CreateManualMatchRequest,
  MatchResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  cancelMatch,
  completeMatch,
  createManualMatch,
  startMatch,
} from '../../api/liveSessionApi'

interface CreateManualMatchState {
  readonly execute: (request: CreateManualMatchRequest) => Promise<boolean>
  readonly isPending: boolean
  readonly errorMessage: string | null
}

export type MatchLifecycleAction =
  | { readonly type: 'START' }
  | { readonly type: 'COMPLETE'; readonly request: CompleteMatchRequest }
  | { readonly type: 'CANCEL' }

interface MatchLifecycleState {
  readonly execute: (action: MatchLifecycleAction) => Promise<void>
  readonly isPending: boolean
  readonly pendingAction: MatchLifecycleAction['type'] | null
  readonly errorMessage: string | null
}

function createErrorMessage(error: Error | null): string | null {
  if (error === null) {
    return null
  }
  if (!(error instanceof HttpError)) {
    return 'Mất kết nối nên chưa xác định được kết quả. Dữ liệu đã được tải lại; hãy kiểm tra Trận chờ bắt đầu trước khi tạo lại.'
  }
  if (error.status === 400) {
    return 'Hãy kiểm tra sân và bốn vị trí người chơi rồi thử lại.'
  }
  if (error.status === 404) {
    return 'Phiên hoặc tài nguyên đã chọn không còn khả dụng. Dữ liệu hiện tại đã được tải lại.'
  }
  if (error.status === 409) {
    return 'Tài nguyên trực tiếp đã thay đổi. Trạng thái phiên hiện tại đã được tải lại.'
  }
  return 'Không thể tạo trận. Trạng thái phiên hiện tại đã được tải lại.'
}

function lifecycleErrorMessage(
  error: Error | null,
  action: MatchLifecycleAction['type'] | null,
): string | null {
  if (error === null) {
    return null
  }
  if (!(error instanceof HttpError)) {
    if (action === 'COMPLETE') {
      return 'Mất kết nối nên chưa xác định được kết quả. Dữ liệu đã được tải lại; hãy kiểm tra trận đã kết thúc hay chưa.'
    }
    if (action === 'CANCEL') {
      return 'Mất kết nối nên chưa xác định được kết quả. Dữ liệu đã được tải lại; hãy kiểm tra trận đã bị hủy hay chưa.'
    }
    return 'Mất kết nối nên chưa xác định được kết quả. Trạng thái phiên đã được tải lại; hãy kiểm tra trận trước khi thử lại.'
  }
  if (error.status === 400) {
    return action === 'COMPLETE'
      ? 'Hãy kiểm tra đội thắng và điểm số tùy chọn rồi thử lại.'
      : 'Không thể hoàn tất thao tác với trận đấu.'
  }
  if (error.status === 404) {
    return 'Trận đấu hoặc tài nguyên bắt buộc không còn khả dụng. Dữ liệu hiện tại đã được tải lại.'
  }
  if (error.status === 409) {
    return action === 'START'
      ? 'Tài nguyên trực tiếp đã thay đổi. Trạng thái phiên hiện tại đã được tải lại.'
      : 'Tài nguyên trực tiếp đã thay đổi. Trạng thái trận hiện tại đã được tải lại.'
  }
  if (action === 'COMPLETE') {
    return 'Không thể kết thúc trận. Trạng thái trận hiện tại đã được tải lại.'
  }
  if (action === 'CANCEL') {
    return 'Không thể hủy trận. Trạng thái trận hiện tại đã được tải lại.'
  }
  return 'Không thể bắt đầu trận. Trạng thái phiên hiện tại đã được tải lại.'
}

function shouldReconcileRuntime(error: Error): boolean {
  return !(error instanceof HttpError) || error.status !== 400
}

export function useCreateManualMatch(
  sessionId: string,
): CreateManualMatchState {
  const queryClient = useQueryClient()
  const inFlight = useRef(false)
  const mutation = useMutation<MatchResponse, Error, CreateManualMatchRequest>({
    mutationFn: (request) => createManualMatch(sessionId, request),
    retry: false,
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['sessionMatches', sessionId],
        exact: true,
      })
    },
    onError: async (error) => {
      if (!shouldReconcileRuntime(error)) {
        return
      }
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ['session', sessionId],
          exact: true,
        }),
        queryClient.invalidateQueries({
          queryKey: ['sessionMatches', sessionId],
          exact: true,
        }),
        queryClient.invalidateQueries({
          queryKey: ['sessionParticipants', sessionId],
          exact: true,
        }),
        queryClient.invalidateQueries({
          queryKey: ['sessionCourts', sessionId],
          exact: true,
        }),
      ])
    },
  })
  const { mutateAsync } = mutation
  const execute = useCallback(
    async (request: CreateManualMatchRequest) => {
      if (inFlight.current) {
        return false
      }
      inFlight.current = true
      try {
        await mutateAsync(request)
        return true
      } catch {
        return false
      } finally {
        inFlight.current = false
      }
    },
    [mutateAsync],
  )

  return {
    execute,
    isPending: mutation.isPending,
    errorMessage: createErrorMessage(mutation.error),
  }
}

export function useMatchLifecycleActions(
  sessionId: string,
  matchId: string,
): MatchLifecycleState {
  const queryClient = useQueryClient()
  const inFlight = useRef(false)
  const mutation = useMutation<MatchResponse, Error, MatchLifecycleAction>({
    mutationFn: (action) => {
      switch (action.type) {
        case 'START':
          return startMatch(matchId)
        case 'COMPLETE':
          return completeMatch(matchId, action.request)
        case 'CANCEL':
          return cancelMatch(matchId)
      }
    },
    retry: false,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ['sessionMatches', sessionId],
          exact: true,
        }),
        queryClient.invalidateQueries({
          queryKey: ['sessionParticipants', sessionId],
          exact: true,
        }),
        queryClient.invalidateQueries({
          queryKey: ['sessionCourts', sessionId],
          exact: true,
        }),
      ])
    },
    onError: async (error, action) => {
      if (!shouldReconcileRuntime(error)) {
        return
      }
      const invalidations = [
        queryClient.invalidateQueries({
          queryKey: ['sessionMatches', sessionId],
          exact: true,
        }),
        queryClient.invalidateQueries({
          queryKey: ['sessionParticipants', sessionId],
          exact: true,
        }),
        queryClient.invalidateQueries({
          queryKey: ['sessionCourts', sessionId],
          exact: true,
        }),
      ]
      if (action.type === 'START') {
        invalidations.push(
          queryClient.invalidateQueries({
            queryKey: ['session', sessionId],
            exact: true,
          }),
        )
      }
      await Promise.all(invalidations)
    },
  })
  const { mutateAsync } = mutation
  const execute = useCallback(
    async (action: MatchLifecycleAction) => {
      if (inFlight.current) {
        return
      }
      inFlight.current = true
      try {
        await mutateAsync(action)
      } catch {
        // The mutation exposes safe scoped feedback after reconciliation.
      } finally {
        inFlight.current = false
      }
    },
    [mutateAsync],
  )

  return {
    execute,
    isPending: mutation.isPending,
    pendingAction: mutation.isPending ? mutation.variables.type : null,
    errorMessage: lifecycleErrorMessage(
      mutation.error,
      mutation.variables?.type ?? null,
    ),
  }
}
