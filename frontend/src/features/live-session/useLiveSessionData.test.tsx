import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
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
import { createLiveSessionInput } from '../../test/liveSessionFixtures'
import {
  LIVE_SESSION_POLL_INTERVAL_MS,
  useLiveSessionData,
} from './useLiveSessionData'

vi.mock('../../api/liveSessionApi', () => ({
  getPlayers: vi.fn(),
  getSession: vi.fn(),
  getSessionCourts: vi.fn(),
  getSessionMatches: vi.fn(),
  getSessionParticipants: vi.fn(),
  getVenue: vi.fn(),
  getVenueCourts: vi.fn(),
}))

const SESSION_ID = 'session-1'

const getPlayersMock = vi.mocked(getPlayers)
const getSessionMock = vi.mocked(getSession)
const getSessionCourtsMock = vi.mocked(getSessionCourts)
const getSessionMatchesMock = vi.mocked(getSessionMatches)
const getSessionParticipantsMock = vi.mocked(getSessionParticipants)
const getVenueMock = vi.mocked(getVenue)
const getVenueCourtsMock = vi.mocked(getVenueCourts)

function deferred<T>() {
  let resolvePromise: (value: T | PromiseLike<T>) => void = () => {
    throw new Error('Deferred promise resolver is unavailable')
  }
  const promise = new Promise<T>((resolve) => {
    resolvePromise = resolve
  })
  return { promise, resolve: resolvePromise }
}

function arrangeSuccessfulReads() {
  const input = createLiveSessionInput()

  getSessionMock.mockResolvedValue(input.session)
  getSessionParticipantsMock.mockResolvedValue(input.participants)
  getSessionCourtsMock.mockResolvedValue(input.sessionCourts)
  getPlayersMock.mockResolvedValue(input.players)
  getSessionMatchesMock.mockResolvedValue(input.matches)
  getVenueMock.mockResolvedValue(input.venue)
  getVenueCourtsMock.mockResolvedValue(input.venueCourts)

  return input
}

function renderLiveSessionData(sessionId = SESSION_ID) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  })

  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        {children}
      </QueryClientProvider>
    )
  }

  const rendered = renderHook(() => useLiveSessionData(sessionId), {
    wrapper: Wrapper,
  })

  return { ...rendered, queryClient }
}

function expectRootReadsOnce() {
  expect(getSessionMock).toHaveBeenCalledTimes(1)
  expect(getSessionMock.mock.calls[0]?.[0]).toBe(SESSION_ID)
  expect(getSessionParticipantsMock).toHaveBeenCalledTimes(1)
  expect(getSessionParticipantsMock.mock.calls[0]?.[0]).toBe(SESSION_ID)
  expect(getSessionCourtsMock).toHaveBeenCalledTimes(1)
  expect(getSessionCourtsMock.mock.calls[0]?.[0]).toBe(SESSION_ID)
  expect(getPlayersMock).toHaveBeenCalledTimes(1)
  expect(getSessionMatchesMock).toHaveBeenCalledTimes(1)
  expect(getSessionMatchesMock.mock.calls[0]?.[0]).toBe(SESSION_ID)
}

describe('useLiveSessionData', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('starts the five root reads from the Session ID before venue data is available', async () => {
    const input = arrangeSuccessfulReads()
    const sessionRead = deferred<typeof input.session>()
    getSessionMock.mockReturnValue(sessionRead.promise)

    const { result, queryClient } = renderLiveSessionData()

    await waitFor(expectRootReadsOnce)
    expect(getVenueMock).not.toHaveBeenCalled()
    expect(getVenueCourtsMock).not.toHaveBeenCalled()

    sessionRead.resolve(input.session)
    await waitFor(() => expect(result.current.status).toBe('ready'))
    queryClient.clear()
  })

  it('waits for Session venueId before starting Venue and Venue Courts reads', async () => {
    const input = arrangeSuccessfulReads()
    const sessionRead = deferred<typeof input.session>()
    getSessionMock.mockReturnValue(sessionRead.promise)

    const { result, queryClient } = renderLiveSessionData()

    await waitFor(() => expect(getSessionMock).toHaveBeenCalledTimes(1))
    expect(getVenueMock).not.toHaveBeenCalled()
    expect(getVenueCourtsMock).not.toHaveBeenCalled()

    sessionRead.resolve(input.session)

    await waitFor(() => expect(result.current.status).toBe('ready'))
    expect(getVenueMock.mock.calls[0]?.[0]).toBe(input.session.venueId)
    expect(getVenueCourtsMock.mock.calls[0]?.[0]).toBe(input.session.venueId)
    queryClient.clear()
  })

  it('performs exactly seven logical API reads for a normal ready load', async () => {
    const input = arrangeSuccessfulReads()
    const { result, queryClient } = renderLiveSessionData()

    await waitFor(() => expect(result.current.status).toBe('ready'))

    expectRootReadsOnce()
    expect(getVenueMock).toHaveBeenCalledTimes(1)
    expect(getVenueMock.mock.calls[0]?.[0]).toBe(input.session.venueId)
    expect(getVenueCourtsMock).toHaveBeenCalledTimes(1)
    expect(getVenueCourtsMock.mock.calls[0]?.[0]).toBe(input.session.venueId)
    queryClient.clear()
  })

  it('polls only the four runtime queries every five seconds', async () => {
    arrangeSuccessfulReads()
    const { result, queryClient } = renderLiveSessionData()

    await waitFor(() => expect(result.current.status).toBe('ready'))

    const intervalFor = (queryKey: readonly string[]) =>
      (queryClient.getQueryCache().find({ queryKey, exact: true })?.options as
        | { readonly refetchInterval?: number }
        | undefined)?.refetchInterval
    expect(intervalFor(['session', SESSION_ID])).toBe(
      LIVE_SESSION_POLL_INTERVAL_MS,
    )
    expect(intervalFor(['sessionParticipants', SESSION_ID])).toBe(
      LIVE_SESSION_POLL_INTERVAL_MS,
    )
    expect(intervalFor(['sessionCourts', SESSION_ID])).toBe(
      LIVE_SESSION_POLL_INTERVAL_MS,
    )
    expect(intervalFor(['sessionMatches', SESSION_ID])).toBe(
      LIVE_SESSION_POLL_INTERVAL_MS,
    )
    expect(intervalFor(['players'])).toBeUndefined()
    expect(intervalFor(['venue', 'venue-1'])).toBeUndefined()
    expect(intervalFor(['venueCourts', 'venue-1'])).toBeUndefined()
    queryClient.clear()
  })

  it('keeps Match polling enabled for a terminal Session', async () => {
    const input = arrangeSuccessfulReads()
    getSessionMock.mockResolvedValue({
      ...input.session,
      status: 'CANCELLED',
      cancelledAt: '2026-09-02T10:00:00Z',
    })
    const { result, queryClient } = renderLiveSessionData()

    await waitFor(() => expect(result.current.status).toBe('ready'))

    expect(
      (queryClient.getQueryCache().find({
        queryKey: ['sessionMatches', SESSION_ID],
        exact: true,
      })?.options as { readonly refetchInterval?: number } | undefined)
        ?.refetchInterval,
    ).toBe(LIVE_SESSION_POLL_INTERVAL_MS)
    queryClient.clear()
  })

  it('keeps cached data ready after a transient background refetch failure', async () => {
    const input = arrangeSuccessfulReads()
    const { result, queryClient } = renderLiveSessionData()
    await waitFor(() => expect(result.current.status).toBe('ready'))

    getSessionMock.mockRejectedValue(new HttpError(404, 'temporarily unavailable'))
    await act(async () => {
      await queryClient.refetchQueries({
        queryKey: ['session', SESSION_ID],
        exact: true,
      })
    })

    await waitFor(() => {
      expect(result.current.status).toBe('ready')
      expect(result.current.hasBackgroundError).toBe(true)
    })
    if (result.current.status === 'ready') {
      expect(result.current.data.session).toEqual(input.session)
    }
    queryClient.clear()
  })

  it('re-reads all seven active queries after manual Refresh', async () => {
    arrangeSuccessfulReads()
    const { result, queryClient } = renderLiveSessionData()

    await waitFor(() => expect(result.current.status).toBe('ready'))

    await act(async () => {
      await result.current.refresh()
    })

    await waitFor(() => {
      expect(getSessionMock).toHaveBeenCalledTimes(2)
      expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2)
      expect(getSessionCourtsMock).toHaveBeenCalledTimes(2)
      expect(getPlayersMock).toHaveBeenCalledTimes(2)
      expect(getSessionMatchesMock).toHaveBeenCalledTimes(2)
      expect(getVenueMock).toHaveBeenCalledTimes(2)
      expect(getVenueCourtsMock).toHaveBeenCalledTimes(2)
    })
    queryClient.clear()
  })

  it('does not start Venue reads when the Session read fails', async () => {
    arrangeSuccessfulReads()
    getSessionMock.mockRejectedValue(
      new HttpError(404, 'Session not found'),
    )

    const { result, queryClient } = renderLiveSessionData()

    await waitFor(() => expect(result.current.status).toBe('not-found'))
    expect(getSessionMock).toHaveBeenCalledTimes(1)
    expect(getVenueMock).not.toHaveBeenCalled()
    expect(getVenueCourtsMock).not.toHaveBeenCalled()
    queryClient.clear()
  })

  it('activates dependent Venue reads after Session retry succeeds', async () => {
    const input = arrangeSuccessfulReads()
    getSessionMock
      .mockRejectedValueOnce(new HttpError(404, 'Session not found'))
      .mockResolvedValue(input.session)

    const { result, queryClient } = renderLiveSessionData()

    await waitFor(() => expect(result.current.status).toBe('not-found'))
    expect(getVenueMock).not.toHaveBeenCalled()
    expect(getVenueCourtsMock).not.toHaveBeenCalled()

    await act(async () => {
      await result.current.refresh()
    })

    await waitFor(() => expect(result.current.status).toBe('ready'))
    expect(getSessionMock).toHaveBeenCalledTimes(2)
    expect(getVenueMock.mock.calls[0]?.[0]).toBe(input.session.venueId)
    expect(getVenueCourtsMock.mock.calls[0]?.[0]).toBe(input.session.venueId)
    queryClient.clear()
  })

  it('retries known Venue context after a Venue read failure', async () => {
    const input = arrangeSuccessfulReads()
    getVenueMock
      .mockRejectedValueOnce(new HttpError(404, 'Venue not found'))
      .mockResolvedValue(input.venue)

    const { result, queryClient } = renderLiveSessionData()

    await waitFor(() => expect(result.current.status).toBe('error'))
    expect(getVenueMock).toHaveBeenCalledTimes(1)
    expect(getVenueCourtsMock).toHaveBeenCalledTimes(1)

    await act(async () => {
      await result.current.refresh()
    })

    await waitFor(() => expect(result.current.status).toBe('ready'))
    expect(getVenueMock).toHaveBeenCalledTimes(2)
    expect(getVenueCourtsMock).toHaveBeenCalledTimes(2)
    queryClient.clear()
  })
})
