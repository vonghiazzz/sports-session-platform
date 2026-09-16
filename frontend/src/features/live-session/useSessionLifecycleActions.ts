import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef } from 'react'
import type { SessionResponse } from '../../api/contracts'
import { HttpError } from '../../api/http'
import { cancelSession, completeSession } from '../../api/liveSessionApi'
import { startSetupSession } from '../../api/sessionSetupApi'

export type SessionLifecycleAction = 'START' | 'COMPLETE' | 'CANCEL'

interface SessionLifecycleState {
  readonly execute: (action: SessionLifecycleAction) => void
  readonly isPending: boolean
  readonly pendingAction: SessionLifecycleAction | null
  readonly errorMessage: string | null
}

function sessionLifecycleErrorMessage(
  error: Error | null,
  action: SessionLifecycleAction | null,
): string | null {
  if (error === null) {
    return null
  }
  if (!(error instanceof HttpError)) {
    if (action === 'START') {
      return 'Mất kết nối nên chưa xác định được kết quả. Dữ liệu đã được tải lại; hãy kiểm tra phiên đã bắt đầu hay chưa.'
    }
    return action === 'COMPLETE'
      ? 'Mất kết nối nên chưa xác định được kết quả. Dữ liệu đã được tải lại; hãy kiểm tra phiên đã kết thúc hay chưa.'
      : 'Mất kết nối nên chưa xác định được kết quả. Dữ liệu đã được tải lại; hãy kiểm tra phiên đã bị hủy hay chưa.'
  }
  if (error.status === 404) {
    return 'Phiên này không còn khả dụng. Dữ liệu vận hành hiện tại đã được tải lại.'
  }
  if (error.status === 409) {
    return 'Trạng thái phiên đã thay đổi. Dữ liệu vận hành hiện tại đã được tải lại.'
  }
  if (action === 'START') {
    return 'Chưa thể xác nhận phiên đã bắt đầu. Dữ liệu vận hành hiện tại đã được tải lại.'
  }
  return action === 'COMPLETE'
    ? 'Chưa thể xác nhận phiên đã kết thúc. Dữ liệu vận hành hiện tại đã được tải lại.'
    : 'Chưa thể xác nhận phiên đã bị hủy. Dữ liệu vận hành hiện tại đã được tải lại.'
}

export function useSessionLifecycleActions(
  sessionId: string,
): SessionLifecycleState {
  const queryClient = useQueryClient()
  const inFlight = useRef(false)
  const mutation = useMutation<SessionResponse, Error, SessionLifecycleAction>({
    mutationFn: (action) => {
      if (action === 'START') {
        return startSetupSession(sessionId)
      }
      return action === 'COMPLETE'
        ? completeSession(sessionId)
        : cancelSession(sessionId)
    },
    retry: false,
    onSettled: async (_data, _error, action) => {
      try {
        const invalidations = [
          queryClient.invalidateQueries({
            queryKey: ['session', sessionId],
            exact: true,
          }),
        ]
        if (action === 'START') {
          invalidations.push(
            queryClient.invalidateQueries({
              queryKey: ['sessions'],
              exact: true,
            }),
          )
        } else {
          invalidations.push(
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
          )
        }
        await Promise.all(invalidations)
      } finally {
        inFlight.current = false
      }
    },
  })
  const { mutate } = mutation
  const execute = useCallback(
    (action: SessionLifecycleAction) => {
      if (inFlight.current) {
        return
      }
      inFlight.current = true
      mutate(action)
    },
    [mutate],
  )

  return {
    execute,
    isPending: mutation.isPending,
    pendingAction: mutation.isPending ? mutation.variables : null,
    errorMessage: sessionLifecycleErrorMessage(
      mutation.error,
      mutation.variables ?? null,
    ),
  }
}
