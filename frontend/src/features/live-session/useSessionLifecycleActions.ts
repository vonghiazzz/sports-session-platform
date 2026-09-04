import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef } from 'react'
import type { SessionResponse } from '../../api/contracts'
import { HttpError } from '../../api/http'
import { cancelSession, completeSession } from '../../api/liveSessionApi'

export type SessionLifecycleAction = 'COMPLETE' | 'CANCEL'

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
    return action === 'COMPLETE'
      ? 'Connection was lost. Session state has been refreshed; check whether this Session completed.'
      : 'Connection was lost. Session state has been refreshed; check whether this Session was cancelled.'
  }
  if (error.status === 404) {
    return 'This Session is no longer available. Current runtime data has been refreshed.'
  }
  if (error.status === 409) {
    return 'Session state changed. Current runtime data has been refreshed.'
  }
  return action === 'COMPLETE'
    ? 'Session completion could not be confirmed. Current runtime data has been refreshed.'
    : 'Session cancellation could not be confirmed. Current runtime data has been refreshed.'
}

export function useSessionLifecycleActions(
  sessionId: string,
): SessionLifecycleState {
  const queryClient = useQueryClient()
  const inFlight = useRef(false)
  const mutation = useMutation<SessionResponse, Error, SessionLifecycleAction>({
    mutationFn: (action) =>
      action === 'COMPLETE'
        ? completeSession(sessionId)
        : cancelSession(sessionId),
    retry: false,
    onSettled: async () => {
      try {
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
