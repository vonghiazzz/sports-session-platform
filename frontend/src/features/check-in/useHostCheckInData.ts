import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useState } from 'react'
import type {
  PlayerResponse,
  SessionParticipantResponse,
  SessionResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  getPlayers,
  getSession,
  getSessionParticipants,
} from '../../api/liveSessionApi'
import {
  LIVE_SESSION_POLL_INTERVAL_MS,
  retryLiveSessionRead,
} from '../live-session/useLiveSessionData'

export interface HostCheckInData {
  readonly session: SessionResponse
  readonly participants: readonly SessionParticipantResponse[]
  readonly players: readonly PlayerResponse[]
}

interface HostCheckInQueryState {
  readonly refresh: () => Promise<void>
  readonly isRefreshing: boolean
  readonly hasBackgroundError?: boolean
}

export type HostCheckInDataState =
  | (HostCheckInQueryState & { readonly status: 'loading' })
  | (HostCheckInQueryState & { readonly status: 'not-found' })
  | (HostCheckInQueryState & { readonly status: 'error' })
  | (HostCheckInQueryState & {
      readonly status: 'ready'
      readonly data: HostCheckInData
    })

export function useHostCheckInData(sessionId: string): HostCheckInDataState {
  const queryClient = useQueryClient()
  const [manualRefreshPending, setManualRefreshPending] = useState(false)
  const enabled = sessionId.length > 0

  const sessionQuery = useQuery({
    queryKey: ['session', sessionId],
    queryFn: ({ signal }) => getSession(sessionId, signal),
    enabled,
    retry: retryLiveSessionRead,
    refetchInterval: LIVE_SESSION_POLL_INTERVAL_MS,
  })
  const participantsQuery = useQuery({
    queryKey: ['sessionParticipants', sessionId],
    queryFn: ({ signal }) => getSessionParticipants(sessionId, signal),
    enabled,
    retry: retryLiveSessionRead,
    refetchInterval: LIVE_SESSION_POLL_INTERVAL_MS,
  })
  const playersQuery = useQuery({
    queryKey: ['players'],
    queryFn: ({ signal }) => getPlayers(signal),
    enabled,
    retry: retryLiveSessionRead,
  })

  const refresh = useCallback(async () => {
    setManualRefreshPending(true)
    try {
      await Promise.all(
        [
          ['session', sessionId],
          ['sessionParticipants', sessionId],
          ['players'],
        ].map((queryKey) =>
          queryClient.invalidateQueries({ queryKey, exact: true }),
        ),
      )
    } finally {
      setManualRefreshPending(false)
    }
  }, [queryClient, sessionId])

  const queries = [sessionQuery, participantsQuery, playersQuery]
  const state = {
    refresh,
    isRefreshing: manualRefreshPending,
    hasBackgroundError: queries.some(
      (query) => query.isError && query.data !== undefined,
    ),
  }

  if (
    sessionQuery.error instanceof HttpError &&
    sessionQuery.error.status === 404 &&
    sessionQuery.data === undefined
  ) {
    return { ...state, status: 'not-found' }
  }
  if (queries.some((query) => query.isError && query.data === undefined)) {
    return { ...state, status: 'error' }
  }
  if (queries.some((query) => query.isPending)) {
    return { ...state, status: 'loading' }
  }
  if (!sessionQuery.data || !participantsQuery.data || !playersQuery.data) {
    return { ...state, status: 'error' }
  }

  return {
    ...state,
    status: 'ready',
    data: {
      session: sessionQuery.data,
      participants: participantsQuery.data,
      players: playersQuery.data,
    },
  }
}
