import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PropsWithChildren } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  GlobalMatchmakingGenerationResponse,
  GlobalMatchmakingQueueResponse,
  MatchPlanResponse,
  MatchRecommendationResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  generateGlobalMatchmakingPreview,
  queueGlobalMatchmakingRecommendations,
} from '../../api/matchmakingApi'
import { GlobalMatchmakingRecommendation } from './GlobalMatchmakingRecommendation'
import type { CourtView, ParticipantView } from './liveSessionModel'

vi.mock('../../api/matchmakingApi', () => ({
  generateGlobalMatchmakingPreview: vi.fn(),
  queueGlobalMatchmakingRecommendations: vi.fn(),
}))

const generateMock = vi.mocked(generateGlobalMatchmakingPreview)
const queueMock = vi.mocked(queueGlobalMatchmakingRecommendations)
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

function queuedPlan(
  recommendationValue: MatchRecommendationResponse,
  id = `plan-${recommendationValue.sessionCourtId}`,
): MatchPlanResponse {
  const assignments = [
    recommendationValue.teamA.slot1,
    recommendationValue.teamA.slot2,
    recommendationValue.teamB.slot1,
    recommendationValue.teamB.slot2,
  ]
  return {
    id,
    sessionId: recommendationValue.sessionId,
    sessionCourtId: recommendationValue.sessionCourtId,
    source: 'RECOMMENDATION',
    status: 'QUEUED',
    queuePosition: 1,
    startedMatchId: null,
    participants: assignments.map((assignment, index) => ({
      id: `${id}-participant-${index + 1}`,
      sessionParticipantId: assignment.sessionParticipantId,
      teamSide: assignment.teamSide,
      teamSlot: assignment.teamSlot,
    })),
    createdAt: '2026-09-02T10:00:01Z',
    startedAt: null,
    cancelledAt: null,
    updatedAt: '2026-09-02T10:00:01Z',
    version: 0,
  }
}

function queueResponse(
  createdPlans: readonly MatchPlanResponse[],
): GlobalMatchmakingQueueResponse {
  return {
    sessionId: 'session-1',
    orchestrationVersion,
    selectionAlgorithmVersion: algorithmVersion,
    createdPlans,
  }
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
    expect(
      screen.getByRole('button', { name: 'Thêm tất cả vào hàng chờ' }),
    ).toBeEnabled()
    expect(
      screen.getAllByRole('button', { name: /hàng chờ/i }),
    ).toHaveLength(1)
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

  it('queues all once with every target Court and exact recommended evidence, then reconciles authoritative Plans', async () => {
    const user = userEvent.setup()
    const recommended = recommendation('court-1', 1)
    const preview = globalPreview([
      recommended,
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
    ])
    const createdPlan = queuedPlan(recommended)
    generateMock.mockResolvedValue(preview)
    queueMock.mockResolvedValue(queueResponse([createdPlan]))
    const { queryClient } = renderGlobal()
    const refetchSpy = vi.spyOn(queryClient, 'refetchQueries')

    await user.click(
      screen.getByRole('button', {
        name: 'Tạo đề xuất cho các sân sẵn sàng',
      }),
    )
    await user.click(
      await screen.findByRole('button', {
        name: 'Thêm tất cả vào hàng chờ',
      }),
    )

    await screen.findByText('Đã thêm tất cả đề xuất vào hàng chờ.')
    expect(queueMock).toHaveBeenCalledOnce()
    expect(queueMock).toHaveBeenCalledWith('session-1', {
      orchestrationVersion,
      selectionAlgorithmVersion: algorithmVersion,
      targetCourtIds: ['court-1', 'court-2'],
      recommendations: [
        {
          sessionCourtId: 'court-1',
          assignments: [
            { sessionParticipantId: 'participant-1', teamSide: 'A', teamSlot: 1 },
            { sessionParticipantId: 'participant-4', teamSide: 'A', teamSlot: 2 },
            { sessionParticipantId: 'participant-2', teamSide: 'B', teamSlot: 1 },
            { sessionParticipantId: 'participant-3', teamSide: 'B', teamSlot: 2 },
          ],
        },
      ],
    })
    expect(refetchSpy).toHaveBeenCalledWith(
      {
        queryKey: ['sessionMatchPlans', 'session-1'],
        exact: true,
        type: 'active',
      },
      { throwOnError: true },
    )
    expect(
      queryClient.getQueryData(['sessionMatchPlans', 'session-1']),
    ).toEqual([createdPlan])
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
  })

  it('guards duplicate Queue-All submission and disables preview actions while pending', async () => {
    const user = userEvent.setup()
    const recommended = recommendation('court-1', 1)
    const pending = deferred<GlobalMatchmakingQueueResponse>()
    generateMock.mockResolvedValue(globalPreview([recommended]))
    queueMock.mockReturnValue(pending.promise)
    renderGlobal()

    await user.click(
      screen.getByRole('button', {
        name: 'Tạo đề xuất cho các sân sẵn sàng',
      }),
    )
    await user.click(
      await screen.findByRole('button', {
        name: 'Thêm tất cả vào hàng chờ',
      }),
    )

    const pendingButton = screen.getByRole('button', {
      name: 'Đang thêm vào hàng chờ…',
    })
    expect(pendingButton).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Tạo lại' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Bỏ đề xuất' })).toBeDisabled()
    await user.click(pendingButton)
    expect(queueMock).toHaveBeenCalledOnce()

    pending.resolve(queueResponse([queuedPlan(recommended)]))
    await screen.findByText('Đã thêm tất cả đề xuất vào hàng chờ.')
  })

  it('keeps a rejected 409 preview visibly stale and requires regeneration before queueing', async () => {
    const user = userEvent.setup()
    const first = recommendation('court-1', 1)
    const regenerated = recommendation('court-2', 5)
    generateMock
      .mockResolvedValueOnce(globalPreview([first]))
      .mockResolvedValueOnce(globalPreview([regenerated]))
    queueMock.mockRejectedValue(new HttpError(409, 'stale evidence'))
    renderGlobal()

    await user.click(
      screen.getByRole('button', {
        name: 'Tạo đề xuất cho các sân sẵn sàng',
      }),
    )
    await user.click(
      await screen.findByRole('button', {
        name: 'Thêm tất cả vào hàng chờ',
      }),
    )

    expect(
      await screen.findByText(/Đề xuất đã thay đổi theo trạng thái mới/),
    ).toBeVisible()
    expect(
      screen.getByRole('article', {
        name: 'Đề xuất toàn phiên cho Sân Một',
      }),
    ).toBeVisible()
    expect(
      screen.queryByRole('button', { name: 'Thêm tất cả vào hàng chờ' }),
    ).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Tạo lại' }))
    expect(
      await screen.findByRole('article', {
        name: 'Đề xuất toàn phiên cho Sân Hai',
      }),
    ).toBeVisible()
    expect(
      screen.getByRole('button', { name: 'Thêm tất cả vào hàng chờ' }),
    ).toBeEnabled()
  })

  it('confirms a transport-rejected request only from exact authoritative MatchPlans', async () => {
    const user = userEvent.setup()
    const recommended = recommendation('court-1', 1)
    generateMock.mockResolvedValue(globalPreview([recommended]))
    queueMock.mockRejectedValue(new Error('connection dropped'))
    const { queryClient } = renderGlobal()
    queryClient.setQueryData(
      ['sessionMatchPlans', 'session-1'],
      [queuedPlan(recommended)],
    )

    await user.click(
      screen.getByRole('button', {
        name: 'Tạo đề xuất cho các sân sẵn sàng',
      }),
    )
    await user.click(
      await screen.findByRole('button', {
        name: 'Thêm tất cả vào hàng chờ',
      }),
    )

    expect(
      await screen.findByText('Đã xác nhận các đề xuất có trong hàng chờ.'),
    ).toBeVisible()
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
    expect(queueMock).toHaveBeenCalledOnce()
  })

  it('keeps partial transport reconciliation unknown and Check Again never repeats POST', async () => {
    const user = userEvent.setup()
    const first = recommendation('court-1', 1)
    const second = recommendation('court-2', 5)
    generateMock.mockResolvedValue(globalPreview([first, second]))
    queueMock.mockRejectedValue(new Error('connection dropped'))
    const { queryClient } = renderGlobal()
    queryClient.setQueryData(
      ['sessionMatchPlans', 'session-1'],
      [queuedPlan(first)],
    )

    await user.click(
      screen.getByRole('button', {
        name: 'Tạo đề xuất cho các sân sẵn sàng',
      }),
    )
    await user.click(
      await screen.findByRole('button', {
        name: 'Thêm tất cả vào hàng chờ',
      }),
    )

    expect(
      await screen.findByText(/Chưa xác định được yêu cầu đã được ghi nhận/),
    ).toBeVisible()
    expect(screen.getByRole('button', { name: 'Kiểm tra lại' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Tạo lại' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Bỏ đề xuất' })).toBeDisabled()

    queryClient.setQueryData(
      ['sessionMatchPlans', 'session-1'],
      [queuedPlan(first), queuedPlan(second)],
    )
    await user.click(screen.getByRole('button', { name: 'Kiểm tra lại' }))

    expect(
      await screen.findByText('Đã xác nhận các đề xuất có trong hàng chờ.'),
    ).toBeVisible()
    expect(queueMock).toHaveBeenCalledOnce()
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
  })

  it('keeps a zero-match transport outcome unknown without blind Queue-All retry', async () => {
    const user = userEvent.setup()
    generateMock.mockResolvedValue(
      globalPreview([recommendation('court-1', 1)]),
    )
    queueMock.mockRejectedValue(new Error('connection dropped'))
    renderGlobal()

    await user.click(
      screen.getByRole('button', {
        name: 'Tạo đề xuất cho các sân sẵn sàng',
      }),
    )
    await user.click(
      await screen.findByRole('button', {
        name: 'Thêm tất cả vào hàng chờ',
      }),
    )

    expect(
      await screen.findByText(/Chưa xác định được yêu cầu đã được ghi nhận/),
    ).toBeVisible()
    expect(screen.getByRole('button', { name: 'Kiểm tra lại' })).toBeEnabled()
    expect(
      screen.queryByRole('button', { name: 'Thêm tất cả vào hàng chờ' }),
    ).not.toBeInTheDocument()
    expect(queueMock).toHaveBeenCalledOnce()
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
    expect(
      screen.queryByRole('button', { name: 'Thêm tất cả vào hàng chờ' }),
    ).not.toBeInTheDocument()
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
