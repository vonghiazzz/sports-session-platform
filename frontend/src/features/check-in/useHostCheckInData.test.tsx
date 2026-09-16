import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getPlayers,
  getSession,
  getSessionParticipants,
} from '../../api/liveSessionApi'
import { createLiveSessionInput } from '../../test/liveSessionFixtures'
import { LIVE_SESSION_POLL_INTERVAL_MS } from '../live-session/useLiveSessionData'
import { useHostCheckInData } from './useHostCheckInData'

vi.mock('../../api/liveSessionApi', () => ({
  getPlayers: vi.fn(),
  getSession: vi.fn(),
  getSessionParticipants: vi.fn(),
}))

const getPlayersMock = vi.mocked(getPlayers)
const getSessionMock = vi.mocked(getSession)
const getSessionParticipantsMock = vi.mocked(getSessionParticipants)

function renderData() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
  }
  const rendered = renderHook(() => useHostCheckInData('session-1'), {
    wrapper: Wrapper,
  })
  return { ...rendered, queryClient }
}

describe('useHostCheckInData', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    const input = createLiveSessionInput()
    getSessionMock.mockResolvedValue(input.session)
    getSessionParticipantsMock.mockResolvedValue(input.participants)
    getPlayersMock.mockResolvedValue(input.players)
  })

  it('loads only the three Check-In Desk sources and shares runtime query keys', async () => {
    const { result, queryClient } = renderData()

    await waitFor(() => expect(result.current.status).toBe('ready'))
    expect(getSessionMock).toHaveBeenCalledWith('session-1', expect.anything())
    expect(getSessionParticipantsMock).toHaveBeenCalledWith(
      'session-1',
      expect.anything(),
    )
    expect(getPlayersMock).toHaveBeenCalledWith(expect.anything())

    const intervalFor = (queryKey: readonly string[]) =>
      (queryClient.getQueryCache().find({ queryKey, exact: true })?.options as
        | { readonly refetchInterval?: number }
        | undefined)?.refetchInterval
    expect(intervalFor(['session', 'session-1'])).toBe(
      LIVE_SESSION_POLL_INTERVAL_MS,
    )
    expect(intervalFor(['sessionParticipants', 'session-1'])).toBe(
      LIVE_SESSION_POLL_INTERVAL_MS,
    )
    expect(intervalFor(['players'])).toBeUndefined()
    queryClient.clear()
  })

  it('keeps ready data visible when a background runtime refresh fails', async () => {
    const { result, queryClient } = renderData()
    await waitFor(() => expect(result.current.status).toBe('ready'))

    getSessionParticipantsMock.mockRejectedValue(new Error('offline'))
    await queryClient.invalidateQueries({
      queryKey: ['sessionParticipants', 'session-1'],
      exact: true,
    })

    await waitFor(() => expect(result.current.hasBackgroundError).toBe(true))
    expect(result.current.status).toBe('ready')
    queryClient.clear()
  })
})
