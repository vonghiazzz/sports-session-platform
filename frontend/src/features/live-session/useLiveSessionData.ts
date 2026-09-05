import {
  useQuery,
  useQueryClient,
  type QueryKey,
} from '@tanstack/react-query'
import { useCallback, useState } from 'react'
import type {
  CourtResponse,
  MatchResponse,
  PlayerResponse,
  SessionCourtResponse,
  SessionParticipantResponse,
  SessionResponse,
  VenueResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  getPlayers,
  getSession,
  getSessionCourts,
  getSessionMatches,
  getSessionParticipants,
  getVenue,
  getVenueCourts,
} from '../../api/liveSessionApi'

export interface LiveSessionData {
  readonly session: SessionResponse
  readonly venue: VenueResponse
  readonly sessionCourts: readonly SessionCourtResponse[]
  readonly venueCourts: readonly CourtResponse[]
  readonly participants: readonly SessionParticipantResponse[]
  readonly players: readonly PlayerResponse[]
  readonly matches: readonly MatchResponse[]
}

interface LiveSessionQueryState {
  readonly refresh: () => Promise<void>
  readonly isRefreshing: boolean
  readonly hasBackgroundError?: boolean
}

export const LIVE_SESSION_POLL_INTERVAL_MS = 5_000

export type LiveSessionDataState =
  | (LiveSessionQueryState & { readonly status: 'loading' })
  | (LiveSessionQueryState & { readonly status: 'not-found' })
  | (LiveSessionQueryState & { readonly status: 'error' })
  | (LiveSessionQueryState & {
      readonly status: 'ready'
      readonly data: LiveSessionData
    })

function retryRead(failureCount: number, error: Error): boolean {
  return !(error instanceof HttpError && error.status === 404) && failureCount < 1
}

export function useLiveSessionData(sessionId: string): LiveSessionDataState {
  const queryClient = useQueryClient()
  const [manualRefreshPending, setManualRefreshPending] = useState(false)
  const enabled = sessionId.length > 0

  const sessionQuery = useQuery({
    queryKey: ['session', sessionId],
    queryFn: ({ signal }) => getSession(sessionId, signal),
    enabled,
    retry: retryRead,
    refetchInterval: LIVE_SESSION_POLL_INTERVAL_MS,
  })
  const participantsQuery = useQuery({
    queryKey: ['sessionParticipants', sessionId],
    queryFn: ({ signal }) => getSessionParticipants(sessionId, signal),
    enabled,
    retry: retryRead,
    refetchInterval: LIVE_SESSION_POLL_INTERVAL_MS,
  })
  const sessionCourtsQuery = useQuery({
    queryKey: ['sessionCourts', sessionId],
    queryFn: ({ signal }) => getSessionCourts(sessionId, signal),
    enabled,
    retry: retryRead,
    refetchInterval: LIVE_SESSION_POLL_INTERVAL_MS,
  })
  const playersQuery = useQuery({
    queryKey: ['players'],
    queryFn: ({ signal }) => getPlayers(signal),
    enabled,
    retry: retryRead,
  })
  const matchesQuery = useQuery({
    queryKey: ['sessionMatches', sessionId],
    queryFn: ({ signal }) => getSessionMatches(sessionId, signal),
    enabled,
    retry: retryRead,
    refetchInterval: LIVE_SESSION_POLL_INTERVAL_MS,
  })

  const venueId = sessionQuery.data?.venueId
  const venueQuery = useQuery({
    queryKey: ['venue', venueId],
    queryFn: ({ signal }) => {
      if (!venueId) {
        return Promise.reject(new Error('Venue ID is not available'))
      }
      return getVenue(venueId, signal)
    },
    enabled: enabled && venueId !== undefined,
    retry: retryRead,
  })
  const venueCourtsQuery = useQuery({
    queryKey: ['venueCourts', venueId],
    queryFn: ({ signal }) => {
      if (!venueId) {
        return Promise.reject(new Error('Venue ID is not available'))
      }
      return getVenueCourts(venueId, signal)
    },
    enabled: enabled && venueId !== undefined,
    retry: retryRead,
  })

  const refresh = useCallback(async () => {
    const queryKeys: QueryKey[] = [
      ['session', sessionId],
      ['sessionParticipants', sessionId],
      ['sessionCourts', sessionId],
      ['players'],
      ['sessionMatches', sessionId],
    ]
    if (venueId) {
      queryKeys.push(['venue', venueId], ['venueCourts', venueId])
    }
    setManualRefreshPending(true)
    try {
      await Promise.all(
        queryKeys.map((queryKey) =>
          queryClient.invalidateQueries({ queryKey, exact: true }),
        ),
      )
    } finally {
      setManualRefreshPending(false)
    }
  }, [queryClient, sessionId, venueId])

  const queries = [
    sessionQuery,
    participantsQuery,
    sessionCourtsQuery,
    playersQuery,
    matchesQuery,
    venueQuery,
    venueCourtsQuery,
  ]
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

  const coreQueries = [
    sessionQuery,
    participantsQuery,
    sessionCourtsQuery,
    playersQuery,
    matchesQuery,
  ]
  if (
    coreQueries.some((query) => query.isPending) ||
    (sessionQuery.data !== undefined &&
      (venueQuery.isPending || venueCourtsQuery.isPending))
  ) {
    return { ...state, status: 'loading' }
  }

  if (
    !sessionQuery.data ||
    !venueQuery.data ||
    !sessionCourtsQuery.data ||
    !venueCourtsQuery.data ||
    !participantsQuery.data ||
    !playersQuery.data ||
    !matchesQuery.data
  ) {
    return { ...state, status: 'error' }
  }

  return {
    ...state,
    status: 'ready',
    data: {
      session: sessionQuery.data,
      venue: venueQuery.data,
      sessionCourts: sessionCourtsQuery.data,
      venueCourts: venueCourtsQuery.data,
      participants: participantsQuery.data,
      players: playersQuery.data,
      matches: matchesQuery.data,
    },
  }
}
