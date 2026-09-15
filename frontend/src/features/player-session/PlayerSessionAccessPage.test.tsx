import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { MatchPlanResponse } from '../../api/contracts'
import { HttpError } from '../../api/http'
import { resolvePlayerSessionAccess } from '../../api/playerSessionAccessApi'
import { createLiveSessionInput } from '../../test/liveSessionFixtures'
import { useLiveSessionData } from '../live-session/useLiveSessionData'
import { PlayerSessionAccessPage } from './PlayerSessionAccessPage'

vi.mock('../../api/playerSessionAccessApi', () => ({
  resolvePlayerSessionAccess: vi.fn(),
}))

vi.mock('../live-session/useLiveSessionData', () => ({
  useLiveSessionData: vi.fn(),
}))

const TOKEN = '550e8400-e29b-41d4-a716-446655440000'
const SESSION_ID = 'session-1'
const resolveAccessMock = vi.mocked(resolvePlayerSessionAccess)
const liveSessionDataMock = vi.mocked(useLiveSessionData)

function queuedPlan(): MatchPlanResponse {
  return {
    id: 'plan-1',
    sessionId: SESSION_ID,
    sessionCourtId: 'session-court-2',
    source: 'RECOMMENDATION',
    status: 'QUEUED',
    queuePosition: 1,
    startedMatchId: null,
    participants: [
      ['participant-1', 'A', 1],
      ['participant-2', 'A', 2],
      ['participant-3', 'B', 1],
      ['participant-4', 'B', 2],
    ].map(([sessionParticipantId, teamSide, teamSlot], index) => ({
      id: `plan-participant-${index + 1}`,
      sessionParticipantId: String(sessionParticipantId),
      teamSide: teamSide as 'A' | 'B',
      teamSlot: Number(teamSlot),
    })),
    createdAt: '2026-09-02T09:55:00Z',
    startedAt: null,
    cancelledAt: null,
    updatedAt: '2026-09-02T09:55:00Z',
    version: 0,
  }
}

function renderRoute(token = TOKEN) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/player-session/${token}`]}>
        <Routes>
          <Route path="/player-session/:token" element={<PlayerSessionAccessPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('PlayerSessionAccessPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it.each([
    ['WAITING', 'participant-1', 'Đang chờ', false],
    ['QUEUED', 'participant-1', 'Đang trong hàng đợi', true],
    ['PLAYING', 'participant-5', 'Đang chơi', false],
  ] as const)(
    'resolves the token and reuses the UUID-based %s Player view',
    async (_state, participantId, stateLabel, queued) => {
      resolveAccessMock.mockResolvedValue({
        sessionId: SESSION_ID,
        sessionParticipantId: participantId,
      })
      const input = createLiveSessionInput()
      liveSessionDataMock.mockReturnValue({
        status: 'ready',
        data: queued ? { ...input, matchPlans: [queuedPlan()] } : input,
        refresh: vi.fn(async () => undefined),
        isRefreshing: false,
      })

      renderRoute()

      expect(await screen.findByText(stateLabel)).toBeVisible()
      expect(resolveAccessMock).toHaveBeenCalledWith(TOKEN, expect.any(AbortSignal))
      expect(liveSessionDataMock).toHaveBeenCalledWith(SESSION_ID)
      expect(screen.queryByText(TOKEN)).not.toBeInTheDocument()
      expect(screen.queryByRole('button')).not.toBeInTheDocument()
    },
  )

  it('shows the safe not-found state for an unknown token', async () => {
    resolveAccessMock.mockRejectedValue(new HttpError(404, 'not found'))

    renderRoute()

    expect(
      await screen.findByRole('heading', { name: 'Không tìm thấy liên kết' }),
    ).toBeVisible()
    expect(liveSessionDataMock).not.toHaveBeenCalled()
    expect(screen.queryByText(TOKEN)).not.toBeInTheDocument()
  })

  it('uses the existing generic error behavior for a malformed token', async () => {
    resolveAccessMock.mockRejectedValue(new HttpError(400, 'invalid token'))

    renderRoute('not-a-uuid')

    expect(
      await screen.findByRole('heading', { name: 'Không thể mở liên kết' }),
    ).toBeVisible()
    expect(screen.getByRole('button', { name: 'Thử lại' })).toBeVisible()
    expect(liveSessionDataMock).not.toHaveBeenCalled()
  })
})
