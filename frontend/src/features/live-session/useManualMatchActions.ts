import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef } from 'react'
import type {
  CreateManualMatchRequest,
  MatchResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import { createManualMatch, startMatch } from '../../api/liveSessionApi'

interface CreateManualMatchState {
  readonly execute: (request: CreateManualMatchRequest) => Promise<boolean>
  readonly isPending: boolean
  readonly errorMessage: string | null
}

interface StartMatchState {
  readonly execute: () => Promise<void>
  readonly isPending: boolean
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

function startErrorMessage(error: Error | null): string | null {
  if (error === null) {
    return null
  }
  if (!(error instanceof HttpError)) {
    return 'Connection was lost. Current Session state has been refreshed; check this Match before trying again.'
  }
  if (error.status === 404) {
    return 'The Match or a required resource is no longer available. Current data has been refreshed.'
  }
  if (error.status === 409) {
    return 'Live resources changed. Current Session state has been refreshed.'
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

export function useStartMatch(
  sessionId: string,
  matchId: string,
): StartMatchState {
  const queryClient = useQueryClient()
  const inFlight = useRef(false)
  const mutation = useMutation<MatchResponse, Error>({
    mutationFn: () => startMatch(matchId),
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
  const execute = useCallback(async () => {
    if (inFlight.current) {
      return
    }
    inFlight.current = true
    try {
      await mutateAsync()
    } catch {
      // The mutation exposes safe scoped feedback after reconciliation.
    } finally {
      inFlight.current = false
    }
  }, [mutateAsync])

  return {
    execute,
    isPending: mutation.isPending,
    errorMessage: startErrorMessage(mutation.error),
  }
}
