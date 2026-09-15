import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  CourtResponse,
  PlayerResponse,
  SessionCourtResponse,
  SessionParticipantResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  addSessionCourt,
  addSessionParticipant,
  createCourt,
  createPlayer,
  getSetupSessionCourts,
  getSetupSessionParticipants,
  getSetupVenueCourts,
} from '../../api/sessionSetupApi'
import {
  useLiveAddCourt,
  useLiveAddPlayer,
} from './useLiveSessionAdditions'

vi.mock('../../api/sessionSetupApi', () => ({
  addSessionCourt: vi.fn(),
  addSessionParticipant: vi.fn(),
  createCourt: vi.fn(),
  createPlayer: vi.fn(),
  getSetupSessionCourts: vi.fn(),
  getSetupSessionParticipants: vi.fn(),
  getSetupVenueCourts: vi.fn(),
}))

const createdPlayer: PlayerResponse = {
  id: 'player-new',
  displayName: 'Người chơi mới',
  sportProfiles: [
    {
      id: 'profile-new',
      sport: 'BADMINTON',
      skillLevel: 'GOOD',
      rating: {
        ratingValue: 35,
        uncertainty: 8.333333333,
        ratedMatches: 0,
        ratingBasis: 'INITIAL_PRIOR',
        ratingAlgorithmVersion: null,
      },
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
    participantCode: 1,
    buddyPairId: null,
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
    defaultOptions: { mutations: { retry: 3 }, queries: { retry: false } },
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
  const court: CourtResponse = {
    id: 'court-new',
    venueId: 'venue-1',
    name: 'Sân mới',
    sport: 'BADMINTON' as const,
    active: true,
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  }

  const createRequest = {
    name: 'Sân mới',
    sport: 'BADMINTON' as const,
    active: true,
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

  it('creates under the Session Venue and allocates only by the returned Court UUID', async () => {
    const venueCourt = { ...court, venueId: 'venue-uuid' }
    vi.mocked(createCourt).mockResolvedValue(venueCourt)
    vi.mocked(getSetupVenueCourts).mockResolvedValue([venueCourt])
    vi.mocked(addSessionCourt).mockResolvedValue(sessionCourt)
    vi.mocked(getSetupSessionCourts).mockResolvedValue([sessionCourt])
    const { result, queryClient } = renderAdditionHook(() =>
      useLiveAddCourt('session-uuid'),
    )

    await act(async () => {
      await result.current.createAndAddCourt('venue-uuid', createRequest)
    })

    expect(createCourt).toHaveBeenCalledWith('venue-uuid', createRequest)
    expect(addSessionCourt).toHaveBeenCalledWith('session-uuid', {
      courtId: 'court-new',
    })
    expect(getSetupVenueCourts).toHaveBeenCalledWith('venue-uuid')
    expect(getSetupSessionCourts).toHaveBeenCalledWith('session-uuid')
    expect(queryClient.getQueryData(['venueCourts', 'venue-uuid'])).toEqual([
      venueCourt,
    ])
    expect(queryClient.getQueryData(['sessionCourts', 'session-uuid'])).toEqual([
      sessionCourt,
    ])
    expect(result.current.recoveryCourt).toBeNull()
    queryClient.clear()
  })

  it('does not insert a created Court into the Court Board before authoritative Session Courts load', async () => {
    const sessionCourtsRead = deferred<readonly SessionCourtResponse[]>()
    vi.mocked(createCourt).mockResolvedValue(court)
    vi.mocked(getSetupVenueCourts).mockResolvedValue([court])
    vi.mocked(addSessionCourt).mockResolvedValue(sessionCourt)
    vi.mocked(getSetupSessionCourts).mockReturnValue(sessionCourtsRead.promise)
    const { result, queryClient } = renderAdditionHook(() =>
      useLiveAddCourt('session-1'),
    )

    let operation: Promise<boolean> | undefined
    act(() => {
      operation = result.current.createAndAddCourt('venue-1', createRequest)
    })
    await waitFor(() => expect(getSetupSessionCourts).toHaveBeenCalledOnce())

    expect(queryClient.getQueryData(['sessionCourts', 'session-1'])).toBeUndefined()

    sessionCourtsRead.resolve([sessionCourt])
    await act(async () => {
      await operation
    })
    expect(queryClient.getQueryData(['sessionCourts', 'session-1'])).toEqual([
      sessionCourt,
    ])
    queryClient.clear()
  })

  it('keeps a created Court for manual allocation when allocation fails', async () => {
    vi.mocked(createCourt).mockResolvedValue(court)
    vi.mocked(getSetupVenueCourts).mockResolvedValue([court])
    vi.mocked(addSessionCourt)
      .mockRejectedValueOnce(new HttpError(409, 'conflict'))
      .mockResolvedValueOnce(sessionCourt)
    vi.mocked(getSetupSessionCourts)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([sessionCourt])
    const { result, queryClient } = renderAdditionHook(() =>
      useLiveAddCourt('session-1'),
    )

    await act(async () => {
      await result.current.createAndAddCourt('venue-1', createRequest)
    })

    expect(result.current.recoveryCourt).toEqual(court)
    expect(result.current.message).toContain(
      'đã được tạo nhưng chưa thể thêm vào phiên',
    )
    expect(queryClient.getQueryData(['venueCourts', 'venue-1'])).toEqual([
      court,
    ])

    await act(async () => {
      await result.current.retryCreatedCourt()
    })

    expect(createCourt).toHaveBeenCalledOnce()
    expect(addSessionCourt).toHaveBeenCalledTimes(2)
    expect(addSessionCourt).toHaveBeenLastCalledWith('session-1', {
      courtId: 'court-new',
    })
    expect(result.current.recoveryCourt).toBeNull()
    queryClient.clear()
  })

  it('does not allocate after a definitive Create Court failure and allows a deliberate retry', async () => {
    vi.mocked(createCourt)
      .mockRejectedValueOnce(new HttpError(409, 'conflict'))
      .mockResolvedValueOnce(court)
    vi.mocked(getSetupVenueCourts).mockResolvedValue([court])
    vi.mocked(addSessionCourt).mockResolvedValue(sessionCourt)
    vi.mocked(getSetupSessionCourts).mockResolvedValue([sessionCourt])
    const { result, queryClient } = renderAdditionHook(() =>
      useLiveAddCourt('session-1'),
    )

    await act(async () => {
      await result.current.createAndAddCourt('venue-1', createRequest)
    })
    expect(addSessionCourt).not.toHaveBeenCalled()
    expect(result.current.message).toContain('tên sân đã tồn tại')

    await act(async () => {
      await result.current.createAndAddCourt('venue-1', createRequest)
    })
    expect(createCourt).toHaveBeenCalledTimes(2)
    expect(addSessionCourt).toHaveBeenCalledOnce()
    queryClient.clear()
  })

  it('prevents duplicate Create Court submission while the first write is pending', async () => {
    const pending = deferred<CourtResponse>()
    vi.mocked(createCourt).mockReturnValue(pending.promise)
    vi.mocked(getSetupVenueCourts).mockResolvedValue([court])
    vi.mocked(addSessionCourt).mockResolvedValue(sessionCourt)
    vi.mocked(getSetupSessionCourts).mockResolvedValue([sessionCourt])
    const { result, queryClient } = renderAdditionHook(() =>
      useLiveAddCourt('session-1'),
    )

    let firstRequest: Promise<boolean> | undefined
    await act(async () => {
      firstRequest = result.current.createAndAddCourt('venue-1', createRequest)
      await result.current.createAndAddCourt('venue-1', createRequest)
    })
    expect(createCourt).toHaveBeenCalledOnce()

    pending.resolve(court)
    await act(async () => {
      await firstRequest
    })
    expect(addSessionCourt).toHaveBeenCalledOnce()
    queryClient.clear()
  })

  it('blocks blind recreation after an unknown Create outcome and recovers from the physical Court list', async () => {
    vi.mocked(createCourt).mockRejectedValue(new TypeError('offline'))
    const { result, queryClient } = renderAdditionHook(() =>
      useLiveAddCourt('session-1'),
    )

    await act(async () => {
      await result.current.createAndAddCourt('venue-1', createRequest)
    })
    expect(result.current.hasUnknownCreateOutcome).toBe(true)
    await act(async () => {
      await result.current.createAndAddCourt('venue-1', createRequest)
    })
    expect(createCourt).toHaveBeenCalledOnce()
    expect(addSessionCourt).not.toHaveBeenCalled()

    vi.mocked(getSetupVenueCourts).mockResolvedValue([court])
    await act(async () => {
      await result.current.reconcileUnknownCreation()
    })
    expect(result.current.hasUnknownCreateOutcome).toBe(false)
    expect(result.current.recoveryCourt).toEqual(court)
    expect(result.current.message).toContain('chưa thêm vào phiên')
    queryClient.clear()
  })
})
