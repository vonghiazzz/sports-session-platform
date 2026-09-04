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
    return 'Connection was lost. Match data has been refreshed. Check Created Matches before creating again.'
  }
  if (error.status === 400) {
    return 'Check the Court and four player assignments, then try again.'
  }
  if (error.status === 404) {
    return 'The Session or a selected resource is no longer available. Current data has been refreshed.'
  }
  if (error.status === 409) {
    return 'Live resources changed. Current Session state has been refreshed.'
  }
  return 'The Match could not be created. Current Session state has been refreshed.'
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
      return 'Connection was lost. Match state has been refreshed; check whether this Match completed.'
    }
    if (action === 'CANCEL') {
      return 'Connection was lost. Match state has been refreshed; check whether this Match was cancelled.'
    }
    return 'Connection was lost. Current Session state has been refreshed; check this Match before trying again.'
  }
  if (error.status === 400) {
    return action === 'COMPLETE'
      ? 'Check the winner and optional scores, then try again.'
      : 'The Match action could not be completed.'
  }
  if (error.status === 404) {
    return 'The Match or a required resource is no longer available. Current data has been refreshed.'
  }
  if (error.status === 409) {
    return action === 'START'
      ? 'Live resources changed. Current Session state has been refreshed.'
      : 'Live resources changed. Current Match state has been refreshed.'
  }
  if (action === 'COMPLETE') {
    return 'The Match could not be completed. Current Match state has been refreshed.'
  }
  if (action === 'CANCEL') {
    return 'The Match could not be cancelled. Current Match state has been refreshed.'
  }
  return 'The Match could not be started. Current Session state has been refreshed.'
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
