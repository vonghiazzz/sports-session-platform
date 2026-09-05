import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  PlayerResponse,
  SessionCourtResponse,
  SessionParticipantResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  addSessionCourt,
  addSessionParticipant,
  createPlayer,
  getSetupSessionCourts,
  getSetupSessionParticipants,
} from '../../api/sessionSetupApi'
import {
  useLiveAddCourt,
  useLiveAddPlayer,
} from './useLiveSessionAdditions'

vi.mock('../../api/sessionSetupApi', () => ({
  addSessionCourt: vi.fn(),
  addSessionParticipant: vi.fn(),
  createPlayer: vi.fn(),
  getSetupSessionCourts: vi.fn(),
  getSetupSessionParticipants: vi.fn(),
}))

const createdPlayer: PlayerResponse = {
  id: 'player-new',
  displayName: 'Người chơi mới',
  sportProfiles: [
    {
      id: 'profile-new',
      sport: 'BADMINTON',
      skillLevel: 'GOOD',
      createdAt: '2026-09-01T00:00:00Z',
      updatedAt: '2026-09-01T00:00:00Z',
    },
  ],
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
}

function participant(playerId: string): SessionParticipantResponse {
  return {
    id: `participant-${playerId}`,
    sessionId: 'session-1',
    playerId,
    status: 'REGISTERED',
    joinedAt: '2026-09-01T00:00:00Z',
    checkedInAt: null,
    waitingSince: null,
    pausedAt: null,
    totalPausedSeconds: 0,
    leftAt: null,
    version: 0,
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  }
}

const sessionCourt: SessionCourtResponse = {
  id: 'session-court-new',
  sessionId: 'session-1',
  courtId: 'court-new',
  status: 'AVAILABLE',
  addedAt: '2026-09-01T00:00:00Z',
  version: 0,
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
}

function deferred<T>() {
  let resolvePromise: (value: T) => void = () => {
    throw new Error('Deferred resolver unavailable')
  }
  const promise = new Promise<T>((resolve) => {
    resolvePromise = resolve
  })
  return { promise, resolve: resolvePromise }
}

function renderAdditionHook<T>(hook: () => T) {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  })
  function Wrapper({ children }: PropsWithChildren) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }
  return { ...renderHook(hook, { wrapper: Wrapper }), queryClient }
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe('useLiveAddPlayer', () => {
  it('posts the selected playerId and reconciles authoritative Participants', async () => {
    const response = participant(createdPlayer.id)
    vi.mocked(addSessionParticipant).mockResolvedValue(response)
    vi.mocked(getSetupSessionParticipants).mockResolvedValue([response])
    const { result, queryClient } = renderAdditionHook(() =>
      useLiveAddPlayer('session-1'),
    )

    await act(async () => {
      await result.current.addExistingPlayer(createdPlayer)
    })

    expect(addSessionParticipant).toHaveBeenCalledWith('session-1', {
      playerId: 'player-new',
    })
    expect(getSetupSessionParticipants).toHaveBeenCalledOnce()
    expect(queryClient.getQueryData(['sessionParticipants', 'session-1'])).toEqual([
      response,
    ])
    queryClient.clear()
  })

  it('guards duplicate add submission while the same request is pending', async () => {
    const pending = deferred<SessionParticipantResponse>()
    vi.mocked(addSessionParticipant).mockReturnValue(pending.promise)
    vi.mocked(getSetupSessionParticipants).mockResolvedValue([
      participant(createdPlayer.id),
    ])
    const { result, queryClient } = renderAdditionHook(() =>
      useLiveAddPlayer('session-1'),
    )

    let firstRequest: Promise<boolean> | undefined
    await act(async () => {
      firstRequest = result.current.addExistingPlayer(createdPlayer)
      await result.current.addExistingPlayer(createdPlayer)
    })
    expect(addSessionParticipant).toHaveBeenCalledOnce()

    pending.resolve(participant(createdPlayer.id))
    await act(async () => {
      await firstRequest
    })
    queryClient.clear()
  })

  it('reuses a created Player when Add Participant fails and is retried', async () => {
    const response = participant(createdPlayer.id)
    vi.mocked(createPlayer).mockResolvedValue(createdPlayer)
    vi.mocked(addSessionParticipant)
      .mockRejectedValueOnce(new HttpError(409, 'conflict'))
      .mockResolvedValue(response)
    vi.mocked(getSetupSessionParticipants)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([response])
    const { result, queryClient } = renderAdditionHook(() =>
      useLiveAddPlayer('session-1'),
    )

    await act(async () => {
      await result.current.createAndAddPlayer({
        displayName: 'Người chơi mới',
        sport: 'BADMINTON',
        skillLevel: 'GOOD',
      })
    })
    expect(result.current.recoveryPlayer).toEqual(createdPlayer)

    await act(async () => {
      await result.current.retryCreatedPlayer()
    })
    expect(createPlayer).toHaveBeenCalledOnce()
    expect(addSessionParticipant).toHaveBeenCalledTimes(2)
    expect(result.current.recoveryPlayer).toBeNull()
    queryClient.clear()
  })

  it('blocks a blind retry until an unknown Add outcome is reconciled', async () => {
    vi.mocked(addSessionParticipant).mockRejectedValue(new TypeError('offline'))
    vi.mocked(getSetupSessionParticipants).mockRejectedValueOnce(new Error('offline'))
    const { result, queryClient } = renderAdditionHook(() =>
      useLiveAddPlayer('session-1'),
    )

    await act(async () => {
      await result.current.addExistingPlayer(createdPlayer)
    })
    expect(result.current.hasUnknownAddOutcome).toBe(true)
    await act(async () => {
      await result.current.addExistingPlayer(createdPlayer)
    })
    expect(addSessionParticipant).toHaveBeenCalledOnce()

    vi.mocked(getSetupSessionParticipants).mockResolvedValue([])
    await act(async () => {
      await result.current.reconcileUnknown()
    })
    expect(result.current.hasUnknownAddOutcome).toBe(false)
    expect(result.current.message).toContain('chưa có trong phiên')
    queryClient.clear()
  })
})

describe('useLiveAddCourt', () => {
  const court = {
    id: 'court-new',
    venueId: 'venue-1',
    name: 'Sân mới',
    sport: 'BADMINTON' as const,
    active: true,
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  }

  it('posts the selected courtId and reconciles authoritative Session Courts', async () => {
    vi.mocked(addSessionCourt).mockResolvedValue(sessionCourt)
    vi.mocked(getSetupSessionCourts).mockResolvedValue([sessionCourt])
    const { result, queryClient } = renderAdditionHook(() =>
      useLiveAddCourt('session-1'),
    )

    await act(async () => {
      await result.current.addCourt(court)
    })

    expect(addSessionCourt).toHaveBeenCalledWith('session-1', {
      courtId: 'court-new',
    })
    expect(queryClient.getQueryData(['sessionCourts', 'session-1'])).toEqual([
      sessionCourt,
    ])
    queryClient.clear()
  })

  it('guards duplicate pending Court allocation', async () => {
    const pending = deferred<SessionCourtResponse>()
    vi.mocked(addSessionCourt).mockReturnValue(pending.promise)
    vi.mocked(getSetupSessionCourts).mockResolvedValue([sessionCourt])
    const { result, queryClient } = renderAdditionHook(() =>
      useLiveAddCourt('session-1'),
    )

    let firstRequest: Promise<boolean> | undefined
    await act(async () => {
      firstRequest = result.current.addCourt(court)
      await result.current.addCourt(court)
    })
    expect(addSessionCourt).toHaveBeenCalledOnce()
    pending.resolve(sessionCourt)
    await act(async () => {
      await firstRequest
    })
    queryClient.clear()
  })

  it('blocks duplicate allocation until an unknown outcome is reconciled', async () => {
    vi.mocked(addSessionCourt).mockRejectedValue(new TypeError('offline'))
    vi.mocked(getSetupSessionCourts).mockRejectedValueOnce(new Error('offline'))
    const { result, queryClient } = renderAdditionHook(() =>
      useLiveAddCourt('session-1'),
    )

    await act(async () => {
      await result.current.addCourt(court)
    })
    expect(result.current.hasUnknownOutcome).toBe(true)
    await act(async () => {
      await result.current.addCourt(court)
    })
    expect(addSessionCourt).toHaveBeenCalledOnce()

    vi.mocked(getSetupSessionCourts).mockResolvedValue([])
    await act(async () => {
      await result.current.reconcileUnknown()
    })
    expect(result.current.hasUnknownOutcome).toBe(false)
    queryClient.clear()
  })
})
