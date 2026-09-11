import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PropsWithChildren } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  GlobalMatchmakingGenerationResponse,
  MatchRecommendationResponse,
} from '../../api/contracts'
import { generateGlobalMatchmakingPreview } from '../../api/matchmakingApi'
import { GlobalMatchmakingRecommendation } from './GlobalMatchmakingRecommendation'
import type { CourtView, ParticipantView } from './liveSessionModel'

vi.mock('../../api/matchmakingApi', () => ({
  generateGlobalMatchmakingPreview: vi.fn(),
}))

const generateMock = vi.mocked(generateGlobalMatchmakingPreview)
const queryClients: QueryClient[] = []
const algorithmVersion =
  'fairness-anchor-level-session-count-rating-sum-v3'
const orchestrationVersion = 'global-greedy-available-unplanned-v1'

const courts: readonly CourtView[] = [
  {
    sessionCourtId: 'court-1',
    name: 'Sân Một',
    status: 'AVAILABLE',
    activeMatch: null,
    dataUnavailable: false,
  },
  {
    sessionCourtId: 'court-2',
    name: 'Sân Hai',
    status: 'AVAILABLE',
    activeMatch: null,
    dataUnavailable: false,
  },
]

const participants: readonly ParticipantView[] = Array.from(
  { length: 8 },
  (_, index) => ({
    sessionParticipantId: `participant-${index + 1}`,
    displayName: `Người ${index + 1}`,
    status: 'WAITING',
    skillLevel: index === 0 ? 'WEAK' : 'INTERMEDIATE',
    skillLabel: index === 0 ? 'Yếu' : 'TB',
    waitingSince: '2026-09-02T09:30:00Z',
    waitingDuration: `${30 - index} phút`,
    dataUnavailable: false,
    plannedMatchCount: 0,
    planningLabel: null,
  }),
)

function recommendedPlayer(
  participantNumber: number,
  teamSide: 'A' | 'B',
  teamSlot: 1 | 2,
) {
  return {
    sessionParticipantId: `participant-${participantNumber}`,
    playerId: `player-${participantNumber}`,
    teamSide,
    teamSlot,
    waitingSince: '2026-09-02T09:30:00Z',
    waitingSeconds: 1800,
    sessionMatchesPlayed: participantNumber === 1 ? 2 : 0,
    ratingValue: 25,
    uncertainty: 8.33,
    ratedMatches: participantNumber === 1 ? 12 : 0,
    ratingBasis: participantNumber === 1 ? 'PERSISTED' : 'INITIAL_PRIOR',
  } as const
}

function recommendation(
  sessionCourtId: string,
  firstParticipant: number,
): MatchRecommendationResponse {
  return {
    outcome: 'RECOMMENDED',
    algorithmVersion,
    evaluationTime: '2026-09-02T10:00:00Z',
    sessionId: 'session-1',
    sessionCourtId,
    sportCode: 'BADMINTON',
    matchFormat: 'DOUBLES',
    eligiblePlayerCount: 8 - firstParticipant + 1,
    teamA: {
      slot1: recommendedPlayer(firstParticipant, 'A', 1),
      slot2: recommendedPlayer(firstParticipant + 3, 'A', 2),
    },
    teamB: {
      slot1: recommendedPlayer(firstParticipant + 1, 'B', 1),
      slot2: recommendedPlayer(firstParticipant + 2, 'B', 2),
    },
    teamARatingTotal: 50,
    teamBRatingTotal: 50,
    ratingDifference: 0,
    oldestWaitingSince: '2026-09-02T09:30:00Z',
  }
}

function globalPreview(
  courtResults: GlobalMatchmakingGenerationResponse['courtResults'],
): GlobalMatchmakingGenerationResponse {
  return {
    outcome: courtResults.some((result) => result.outcome === 'RECOMMENDED')
      ? 'RECOMMENDED'
      : 'UNAVAILABLE',
    orchestrationVersion,
    selectionAlgorithmVersion: algorithmVersion,
    evaluationTime: '2026-09-02T10:00:00Z',
    sessionId: 'session-1',
    initialEligiblePlayerCount: 8,
    courtResults,
    reason: null,
  }
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

function renderGlobal() {
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
    <GlobalMatchmakingRecommendation
      sessionId="session-1"
      courts={courts}
      participants={participants}
    />,
    { wrapper: Wrapper },
  )
  return { ...rendered, queryClient }
}

beforeEach(() => {
  vi.resetAllMocks()
})

afterEach(() => {
  queryClients.forEach((queryClient) => queryClient.clear())
  queryClients.length = 0
})

describe('Global Matchmaking recommendation', () => {
  it('generates exactly once while pending and disables duplicate submission', async () => {
    const user = userEvent.setup()
    const request = deferred<GlobalMatchmakingGenerationResponse>()
    generateMock.mockReturnValue(request.promise)
    const { queryClient } = renderGlobal()

    await user.click(
      screen.getByRole('button', {
        name: 'Tạo đề xuất cho các sân sẵn sàng',
      }),
    )

    const pendingButton = screen.getByRole('button', {
      name: 'Đang tạo đề xuất toàn phiên…',
    })
    expect(pendingButton).toBeDisabled()
    await user.click(pendingButton)
    expect(generateMock).toHaveBeenCalledOnce()
    expect(
      queryClient
        .getMutationCache()
        .getAll()
        .map((mutation) => mutation.options.retry),
    ).toEqual([false])

    request.resolve(globalPreview([recommendation('court-1', 1)]))
    await screen.findByRole('button', { name: 'Tạo lại' })
  })

  it('renders recommended Courts in backend order with names and player evidence', async () => {
    const user = userEvent.setup()
    generateMock.mockResolvedValue(
      globalPreview([
        recommendation('court-2', 5),
        recommendation('court-1', 1),
      ]),
    )
    renderGlobal()

    await user.click(
      screen.getByRole('button', {
        name: 'Tạo đề xuất cho các sân sẵn sàng',
      }),
    )

    const resultCards = await screen.findAllByRole('article')
    expect(resultCards.map((card) => card.getAttribute('aria-label'))).toEqual([
      'Đề xuất toàn phiên cho Sân Hai',
      'Đề xuất toàn phiên cho Sân Một',
    ])

    const first = within(resultCards[0])
    expect(first.getByText('Người 5')).toBeVisible()
    expect(first.getByText('Người 8')).toBeVisible()
    expect(first.getAllByText('Trình độ: TB')).toHaveLength(4)
    expect(first.getAllByText('Trong phiên: 0 trận hoàn tất')).toHaveLength(4)
    expect(first.getAllByText('Rating: 25,0 · Điểm khởi tạo')).toHaveLength(4)
    expect(
      first.getByRole('heading', { name: 'Đội A · Tổng Rating 50,0' }),
    ).toBeVisible()
    expect(first.getByText(/Chênh lệch Rating giữa hai đội:/)).toBeVisible()

    const second = within(resultCards[1])
    expect(second.getByText('Trình độ: Yếu')).toBeVisible()
    expect(second.getByText('Trong phiên: 2 trận hoàn tất')).toBeVisible()
    expect(second.getByText('Rating: 25,0 · rating từ 12 trận')).toBeVisible()
    expect(screen.queryByRole('button', { name: /hàng chờ/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /chấp nhận/i })).not.toBeInTheDocument()
  })

  it('renders a recommended result followed by a normal unavailable result', async () => {
    const user = userEvent.setup()
    generateMock.mockResolvedValue(
      globalPreview([
        recommendation('court-1', 1),
        {
          outcome: 'UNAVAILABLE',
          algorithmVersion,
          evaluationTime: '2026-09-02T10:00:00Z',
          sessionId: 'session-1',
          sessionCourtId: 'court-2',
          sportCode: 'BADMINTON',
          matchFormat: 'DOUBLES',
          eligiblePlayerCount: 3,
          reason: 'INSUFFICIENT_ELIGIBLE_PLAYERS',
        },
      ]),
    )
    renderGlobal()

    await user.click(
      screen.getByRole('button', {
        name: 'Tạo đề xuất cho các sân sẵn sàng',
      }),
    )

    expect(
      await screen.findByRole('article', {
        name: 'Đề xuất toàn phiên cho Sân Một',
      }),
    ).toBeVisible()
    const unavailable = screen.getByRole('article', {
      name: 'Không có đề xuất toàn phiên cho Sân Hai',
    })
    expect(
      within(unavailable).getByText(/Không còn đủ bốn người chơi/),
    ).toBeVisible()
    expect(within(unavailable).getByText('Còn 3 người đủ điều kiện')).toBeVisible()
  })

  it('treats a no-eligible-Court response as a normal informative state', async () => {
    const user = userEvent.setup()
    generateMock.mockResolvedValue({
      outcome: 'UNAVAILABLE',
      orchestrationVersion,
      selectionAlgorithmVersion: algorithmVersion,
      evaluationTime: '2026-09-02T10:00:00Z',
      sessionId: 'session-1',
      initialEligiblePlayerCount: 8,
      courtResults: [],
      reason: 'NO_ELIGIBLE_COURTS',
    })
    renderGlobal()

    await user.click(
      screen.getByRole('button', {
        name: 'Tạo đề xuất cho các sân sẵn sàng',
      }),
    )

    expect(
      await screen.findByText(
        'Hiện không có sân sẵn sàng để tạo đề xuất.',
      ),
    ).toBeVisible()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('regenerates authoritatively, preserves the last preview on failure, and dismisses locally', async () => {
    const user = userEvent.setup()
    generateMock
      .mockResolvedValueOnce(globalPreview([recommendation('court-1', 1)]))
      .mockResolvedValueOnce(globalPreview([recommendation('court-2', 5)]))
      .mockRejectedValueOnce(new Error('network unavailable'))
    renderGlobal()

    await user.click(
      screen.getByRole('button', {
        name: 'Tạo đề xuất cho các sân sẵn sàng',
      }),
    )
    expect(
      await screen.findByRole('article', {
        name: 'Đề xuất toàn phiên cho Sân Một',
      }),
    ).toBeVisible()

    await user.click(screen.getByRole('button', { name: 'Tạo lại' }))
    expect(
      await screen.findByRole('article', {
        name: 'Đề xuất toàn phiên cho Sân Hai',
      }),
    ).toBeVisible()
    expect(
      screen.queryByRole('article', {
        name: 'Đề xuất toàn phiên cho Sân Một',
      }),
    ).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Tạo lại' }))
    await waitFor(() => expect(screen.getByRole('alert')).toBeVisible())
    expect(
      screen.getByRole('article', {
        name: 'Đề xuất toàn phiên cho Sân Hai',
      }),
    ).toBeVisible()

    await user.click(screen.getByRole('button', { name: 'Bỏ đề xuất' }))
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    expect(
      screen.getByRole('button', {
        name: 'Tạo đề xuất cho các sân sẵn sàng',
      }),
    ).toBeEnabled()
  })
})
