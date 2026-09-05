import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PropsWithChildren } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  MatchRecommendationResponse,
  MatchResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  acceptMatchmakingRecommendation,
  generateMatchmakingRecommendation,
} from '../../api/matchmakingApi'
import type { CourtView, ParticipantView } from './liveSessionModel'
import { MatchmakingRecommendation } from './MatchmakingRecommendation'

vi.mock('../../api/matchmakingApi', () => ({
  acceptMatchmakingRecommendation: vi.fn(),
  generateMatchmakingRecommendation: vi.fn(),
}))

const generateMock = vi.mocked(generateMatchmakingRecommendation)
const acceptMock = vi.mocked(acceptMatchmakingRecommendation)
const queryClients: QueryClient[] = []

const court: CourtView = {
  sessionCourtId: 'session-court-2',
  name: 'Sân Hai',
  status: 'AVAILABLE',
  activeMatch: null,
  dataUnavailable: false,
}

const participants: readonly ParticipantView[] = [
  ['participant-1', 'An Nguyen', 'WEAK', 'Yếu', '30 phút'],
  ['participant-2', 'Bao Tran', 'WEAK_PLUS', 'Yếu+', '25 phút'],
  ['participant-3', 'Chi Le', 'INTERMEDIATE_MINUS', 'TB-', '20 phút'],
  ['participant-4', 'Dung Pham', 'INTERMEDIATE', 'TB', '15 phút'],
].map(([sessionParticipantId, displayName, skillLevel, skillLabel, waitingDuration]) => ({
  sessionParticipantId,
  displayName,
  status: 'WAITING',
  skillLevel,
  skillLabel,
  waitingSince: '2026-09-02T09:30:00Z',
  waitingDuration,
  dataUnavailable: false,
})) as readonly ParticipantView[]

function recommendedPlayer(
  sessionParticipantId: string,
  playerId: string,
  teamSide: 'A' | 'B',
  teamSlot: 1 | 2,
) {
  return {
    sessionParticipantId,
    playerId,
    teamSide,
    teamSlot,
    waitingSince: '2026-09-02T09:30:00Z',
    waitingSeconds: 1800,
    ratingValue: 25,
    uncertainty: 8.33,
    ratedMatches: 0,
    ratingBasis: 'INITIAL_PRIOR' as const,
  }
}

const recommendation: MatchRecommendationResponse = {
  outcome: 'RECOMMENDED',
  algorithmVersion: 'fairness-anchor-rating-sum-v1',
  evaluationTime: '2026-09-02T10:00:00Z',
  sessionId: 'session-1',
  sessionCourtId: court.sessionCourtId,
  sportCode: 'BADMINTON',
  matchFormat: 'DOUBLES',
  eligiblePlayerCount: 5,
  teamA: {
    slot1: recommendedPlayer('participant-1', 'player-1', 'A', 1),
    slot2: recommendedPlayer('participant-4', 'player-4', 'A', 2),
  },
  teamB: {
    slot1: recommendedPlayer('participant-2', 'player-2', 'B', 1),
    slot2: recommendedPlayer('participant-3', 'player-3', 'B', 2),
  },
  teamARatingTotal: 50,
  teamBRatingTotal: 50,
  ratingDifference: 0,
  oldestWaitingSince: '2026-09-02T09:30:00Z',
}

const acceptedMatch: MatchResponse = {
  id: 'recommended-match-1',
  sessionId: 'session-1',
  sessionCourtId: court.sessionCourtId,
  status: 'PLAYING',
  source: 'RECOMMENDATION',
  winnerTeam: null,
  teamAScore: null,
  teamBScore: null,
  resultVersion: 0,
  participants: [
    { sessionParticipantId: 'participant-1', teamSide: 'A', teamSlot: 1 },
    { sessionParticipantId: 'participant-4', teamSide: 'A', teamSlot: 2 },
    { sessionParticipantId: 'participant-2', teamSide: 'B', teamSlot: 1 },
    { sessionParticipantId: 'participant-3', teamSide: 'B', teamSlot: 2 },
  ],
  createdAt: '2026-09-02T10:00:00Z',
  startedAt: '2026-09-02T10:00:00Z',
  completedAt: null,
  cancelledAt: null,
  updatedAt: '2026-09-02T10:00:00Z',
  version: 1,
}

function deferred<T>() {
  let resolvePromise: (value: T | PromiseLike<T>) => void = () => {
    throw new Error('Deferred promise resolver is unavailable')
  }
  const promise = new Promise<T>((resolve) => {
    resolvePromise = resolve
  })
  return { promise, resolve: resolvePromise }
}

function renderRecommendation() {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: { retry: false },
      queries: { retry: false },
    },
  })
  queryClients.push(queryClient)
  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={queryClient}>
        {children}
      </QueryClientProvider>
    )
  }
  const rendered = render(
    <MatchmakingRecommendation
      sessionId="session-1"
      court={court}
      participants={participants}
    />,
    { wrapper: Wrapper },
  )
  return { ...rendered, queryClient }
}

async function generate(user: ReturnType<typeof userEvent.setup>) {
  generateMock.mockResolvedValue(recommendation)
  await user.click(screen.getByRole('button', { name: 'Tạo đề xuất' }))
  await screen.findByRole('button', { name: 'Chấp nhận & bắt đầu' })
}

beforeEach(() => {
  vi.resetAllMocks()
})

afterEach(() => {
  queryClients.forEach((queryClient) => queryClient.clear())
  queryClients.length = 0
})

describe('Matchmaking recommendation', () => {
  it('shows the generated teams under the correct Court without raw identifiers', async () => {
    const user = userEvent.setup()
    renderRecommendation()
    await generate(user)

    const proposal = screen.getByRole('region', {
      name: 'Đề xuất trận cho Sân Hai',
    })
    expect(within(proposal).getByRole('heading', { name: 'Sân Hai' })).toBeVisible()
    expect(within(proposal).getByRole('heading', { name: 'Đội A' })).toBeVisible()
    expect(within(proposal).getByRole('heading', { name: 'Đội B' })).toBeVisible()
    expect(within(proposal).getByText('An Nguyen')).toBeVisible()
    expect(within(proposal).getByText('Yếu')).toBeVisible()
    expect(within(proposal).getByText('Chờ 30 phút')).toBeVisible()
    expect(proposal).not.toHaveTextContent('participant-1')
    expect(proposal).not.toHaveTextContent('fairness-anchor-rating-sum-v1')
    expect(acceptMock).not.toHaveBeenCalled()
  })

  it('guards duplicate Generate and does not retry it', async () => {
    const user = userEvent.setup()
    const request = deferred<MatchRecommendationResponse>()
    generateMock.mockReturnValue(request.promise)
    const { queryClient } = renderRecommendation()

    const button = screen.getByRole('button', { name: 'Tạo đề xuất' })
    await user.click(button)
    await user.click(screen.getByRole('button', { name: 'Đang tạo đề xuất…' }))
    expect(generateMock).toHaveBeenCalledOnce()
    expect(
      queryClient
        .getMutationCache()
        .getAll()
        .map((mutation) => mutation.options.retry),
    ).toEqual([false])
    expect(
      queryClient
        .getQueryCache()
        .findAll()
        .some((query) => query.queryKey.includes('match-recommendations')),
    ).toBe(false)

    request.resolve(recommendation)
    await screen.findByRole('button', { name: 'Chấp nhận & bắt đầu' })
  })

  it('dismisses only local state and leaves Manual Match as the fallback', async () => {
    const user = userEvent.setup()
    renderRecommendation()
    await generate(user)

    await user.click(screen.getByRole('button', { name: 'Bỏ đề xuất' }))

    expect(screen.getByRole('button', { name: 'Tạo đề xuất' })).toBeEnabled()
    expect(screen.getByText(/tạo trận thủ công bên dưới/i)).toBeVisible()
    expect(acceptMock).not.toHaveBeenCalled()
  })

  it('shows an unavailable outcome as scoped guidance without fabricating teams', async () => {
    const user = userEvent.setup()
    generateMock.mockResolvedValue({
      outcome: 'UNAVAILABLE',
      algorithmVersion: recommendation.algorithmVersion,
      evaluationTime: recommendation.evaluationTime,
      sessionId: recommendation.sessionId,
      sessionCourtId: recommendation.sessionCourtId,
      sportCode: recommendation.sportCode,
      matchFormat: recommendation.matchFormat,
      eligiblePlayerCount: 3,
      reason: 'INSUFFICIENT_ELIGIBLE_PLAYERS',
    })
    renderRecommendation()

    await user.click(screen.getByRole('button', { name: 'Tạo đề xuất' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Chưa đủ bốn người chơi',
    )
    expect(
      screen.queryByRole('button', { name: 'Chấp nhận & bắt đầu' }),
    ).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tạo đề xuất mới' })).toBeEnabled()
  })

  it('submits exact evidence once and reconciles all four runtime queries', async () => {
    const user = userEvent.setup()
    acceptMock.mockResolvedValue(acceptedMatch)
    const { queryClient } = renderRecommendation()
    const refetch = vi.spyOn(queryClient, 'refetchQueries')
    await generate(user)

    await user.click(
      screen.getByRole('button', { name: 'Chấp nhận & bắt đầu' }),
    )

    await waitFor(() => expect(acceptMock).toHaveBeenCalledOnce())
    expect(
      queryClient
        .getMutationCache()
        .getAll()
        .map((mutation) => mutation.options.retry),
    ).toEqual([false, false])
    expect(acceptMock).toHaveBeenCalledWith('session-1', 'session-court-2', {
      algorithmVersion: 'fairness-anchor-rating-sum-v1',
      assignments: [
        { sessionParticipantId: 'participant-1', teamSide: 'A', teamSlot: 1 },
        { sessionParticipantId: 'participant-4', teamSide: 'A', teamSlot: 2 },
        { sessionParticipantId: 'participant-2', teamSide: 'B', teamSlot: 1 },
        { sessionParticipantId: 'participant-3', teamSide: 'B', teamSlot: 2 },
      ],
    })
    await waitFor(() => expect(refetch).toHaveBeenCalledTimes(4))
    expect(refetch.mock.calls.map(([filters]) => filters?.queryKey)).toEqual([
      ['session', 'session-1'],
      ['sessionMatches', 'session-1'],
      ['sessionParticipants', 'session-1'],
      ['sessionCourts', 'session-1'],
    ])
    expect(
      screen.queryByRole('button', { name: 'Chấp nhận & bắt đầu' }),
    ).not.toBeInTheDocument()
  })

  it('guards duplicate Accept while the first request is pending', async () => {
    const user = userEvent.setup()
    const request = deferred<MatchResponse>()
    acceptMock.mockReturnValue(request.promise)
    renderRecommendation()
    await generate(user)

    await user.click(
      screen.getByRole('button', { name: 'Chấp nhận & bắt đầu' }),
    )
    const pendingButton = screen.getByRole('button', {
      name: 'Đang chấp nhận…',
    })
    await user.click(pendingButton)
    expect(acceptMock).toHaveBeenCalledOnce()

    request.resolve(acceptedMatch)
    await waitFor(() =>
      expect(
        screen.queryByRole('button', { name: 'Đang chấp nhận…' }),
      ).not.toBeInTheDocument(),
    )
  })

  it('keeps a stale failure scoped and requires a newly generated proposal', async () => {
    const user = userEvent.setup()
    acceptMock.mockRejectedValue(new HttpError(409, 'Submitted recommendation is stale'))
    renderRecommendation()
    await generate(user)

    await user.click(
      screen.getByRole('button', { name: 'Chấp nhận & bắt đầu' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Đề xuất không còn hợp lệ',
    )
    expect(
      screen.getByRole('button', { name: 'Chấp nhận & bắt đầu' }),
    ).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Tạo đề xuất mới' })).toBeEnabled()
    expect(screen.getByText(/tạo trận thủ công bên dưới/i)).toBeVisible()
    expect(acceptMock).toHaveBeenCalledOnce()
  })

  it('uses reconciled server truth after a lost Accept response', async () => {
    const user = userEvent.setup()
    acceptMock.mockRejectedValue(new TypeError('Failed to fetch'))
    const { queryClient } = renderRecommendation()
    queryClient.setQueryData(['sessionMatches', 'session-1'], [acceptedMatch])
    await generate(user)

    await user.click(
      screen.getByRole('button', { name: 'Chấp nhận & bắt đầu' }),
    )

    await waitFor(() =>
      expect(
        screen.queryByRole('button', { name: 'Chấp nhận & bắt đầu' }),
      ).not.toBeInTheDocument(),
    )
    expect(acceptMock).toHaveBeenCalledOnce()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('blocks blind repeat when Accept and authoritative reconciliation are uncertain', async () => {
    const user = userEvent.setup()
    acceptMock.mockRejectedValue(new TypeError('Failed to fetch'))
    const { queryClient } = renderRecommendation()
    vi.spyOn(queryClient, 'refetchQueries').mockRejectedValue(
      new TypeError('Failed to refresh'),
    )
    await generate(user)

    await user.click(
      screen.getByRole('button', { name: 'Chấp nhận & bắt đầu' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'không chấp nhận lại đề xuất này',
    )
    const acceptButton = screen.getByRole('button', {
      name: 'Chấp nhận & bắt đầu',
    })
    expect(acceptButton).toBeDisabled()
    await user.click(acceptButton)
    expect(acceptMock).toHaveBeenCalledOnce()
  })
})
