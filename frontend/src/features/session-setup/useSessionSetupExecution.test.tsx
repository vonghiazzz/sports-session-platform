import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  addSessionCourt,
  addSessionParticipant,
  createSession,
  getSetupSession,
  getSetupSessionCourts,
  getSetupSessionParticipants,
  startSetupSession,
} from '../../api/sessionSetupApi'
import { useSessionSetupExecution } from './useSessionSetupExecution'

const navigateMock = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => navigateMock,
}))

vi.mock('../../api/sessionSetupApi', () => ({
  addSessionCourt: vi.fn(),
  addSessionParticipant: vi.fn(),
  createSession: vi.fn(),
  getSetupSession: vi.fn(),
  getSetupSessionCourts: vi.fn(),
  getSetupSessionParticipants: vi.fn(),
  startSetupSession: vi.fn(),
}))

const input = {
  session: {
    venueId: 'venue-1',
    title: 'Phiên tối',
    sport: 'BADMINTON' as const,
    matchFormat: 'DOUBLES' as const,
    plannedStartAt: '2026-09-05T11:00:00.000Z',
    plannedEndAt: '2026-09-05T13:00:00.000Z',
  },
  courtIds: ['court-1', 'court-2'],
  playerIds: ['player-1', 'player-2'],
}

function session(status: 'PLANNED' | 'IN_PROGRESS') {
  return {
    id: 'session-1',
    ...input.session,
    status,
    startedAt: status === 'IN_PROGRESS' ? '2026-09-05T11:00:00.000Z' : null,
    completedAt: null,
    cancelledAt: null,
    version: 0,
    createdAt: '2026-09-01T00:00:00.000Z',
    updatedAt: '2026-09-01T00:00:00.000Z',
  }
}

function renderExecution() {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  })
  function Wrapper({ children }: PropsWithChildren) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }
  return { ...renderHook(() => useSessionSetupExecution(), { wrapper: Wrapper }), queryClient }
}

describe('useSessionSetupExecution', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(createSession).mockResolvedValue(session('PLANNED'))
    vi.mocked(getSetupSession).mockResolvedValue(session('PLANNED'))
    vi.mocked(getSetupSessionCourts).mockResolvedValue([])
    vi.mocked(getSetupSessionParticipants).mockResolvedValue([])
    vi.mocked(addSessionCourt).mockResolvedValue({} as never)
    vi.mocked(addSessionParticipant).mockResolvedValue({} as never)
    vi.mocked(startSetupSession).mockResolvedValue(session('IN_PROGRESS'))
  })

  it('creates, allocates, starts, and navigates using the returned Session ID', async () => {
    const { result, queryClient } = renderExecution()

    act(() => result.current.execute(input))

    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/sessions/session-1'))
    expect(createSession).toHaveBeenCalledOnce()
    expect(addSessionCourt).toHaveBeenCalledTimes(2)
    expect(addSessionParticipant).toHaveBeenCalledTimes(2)
    expect(startSetupSession).toHaveBeenCalledWith('session-1')
    queryClient.clear()
  })

  it('keeps the Session ID and does not recreate after a partial allocation failure', async () => {
    vi.mocked(addSessionParticipant)
      .mockResolvedValueOnce({} as never)
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValue({} as never)
    const { result, queryClient } = renderExecution()

    act(() => result.current.execute(input))
    await waitFor(() => expect(result.current.errorMessage).toContain('đủ người chơi'))
    expect(result.current.sessionId).toBe('session-1')

    vi.mocked(getSetupSessionCourts).mockResolvedValue([
      { courtId: 'court-1' },
      { courtId: 'court-2' },
    ] as never)
    vi.mocked(getSetupSessionParticipants).mockResolvedValue([
      { playerId: 'player-1' },
    ] as never)
    act(() => result.current.execute(input))

    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/sessions/session-1'))
    expect(createSession).toHaveBeenCalledOnce()
    expect(addSessionParticipant).toHaveBeenLastCalledWith('session-1', {
      playerId: 'player-2',
    })
    queryClient.clear()
  })

  it('reconciles an unknown Start result before navigating', async () => {
    vi.mocked(startSetupSession).mockRejectedValue(new Error('offline'))
    vi.mocked(getSetupSession)
      .mockResolvedValueOnce(session('PLANNED'))
      .mockResolvedValueOnce(session('IN_PROGRESS'))
    const { result, queryClient } = renderExecution()

    act(() => result.current.execute(input))

    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/sessions/session-1'))
    expect(getSetupSession).toHaveBeenCalledTimes(2)
    queryClient.clear()
  })

  it('blocks blind resubmission when Create Session has an unknown outcome', async () => {
    vi.mocked(createSession).mockRejectedValue(new TypeError('Failed to fetch'))
    const { result, queryClient } = renderExecution()

    act(() => result.current.execute(input))
    await waitFor(() => expect(result.current.unknownCreateOutcome).toBe(true))
    act(() => result.current.execute(input))

    expect(createSession).toHaveBeenCalledOnce()
    expect(result.current.errorMessage).toContain('tránh tạo trùng')
    queryClient.clear()
  })
})
