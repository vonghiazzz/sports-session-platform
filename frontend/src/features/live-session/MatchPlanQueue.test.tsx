import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  MatchPlanResponse,
  ParticipantStatus,
  SessionParticipantResponse,
} from '../../api/contracts'
import {
  cancelMatchPlan,
  createMatchPlan,
  moveMatchPlan,
  reorderMatchPlan,
  startMatchPlan,
  updateMatchPlan,
} from '../../api/matchPlanApi'
import { createLiveSessionInput } from '../../test/liveSessionFixtures'
import { composeLiveSessionModel } from './liveSessionModel'
import { MatchPlanQueue } from './MatchPlanQueue'
import { matchPlanStartReason } from './matchPlanReadiness'

vi.mock('../../api/matchPlanApi', () => ({
  cancelMatchPlan: vi.fn(),
  createMatchPlan: vi.fn(),
  getSessionMatchPlans: vi.fn(),
  moveMatchPlan: vi.fn(),
  reorderMatchPlan: vi.fn(),
  startMatchPlan: vi.fn(),
  updateMatchPlan: vi.fn(),
}))

const createMock = vi.mocked(createMatchPlan)
const updateMock = vi.mocked(updateMatchPlan)
const moveMock = vi.mocked(moveMatchPlan)
const reorderMock = vi.mocked(reorderMatchPlan)
const cancelMock = vi.mocked(cancelMatchPlan)
const startMock = vi.mocked(startMatchPlan)
const queryClients: QueryClient[] = []

const assignments = [
  { id: 'pa-1', sessionParticipantId: 'participant-1', teamSide: 'A' as const, teamSlot: 1 },
  { id: 'pa-2', sessionParticipantId: 'participant-2', teamSide: 'A' as const, teamSlot: 2 },
  { id: 'pa-3', sessionParticipantId: 'participant-3', teamSide: 'B' as const, teamSlot: 1 },
  { id: 'pa-4', sessionParticipantId: 'participant-4', teamSide: 'B' as const, teamSlot: 2 },
]

function plan(
  id: string,
  sessionCourtId: string,
  queuePosition: number,
  status: MatchPlanResponse['status'] = 'QUEUED',
): MatchPlanResponse {
  return {
    id,
    sessionId: 'session-1',
    sessionCourtId,
    source: 'MANUAL',
    status,
    queuePosition: status === 'QUEUED' ? queuePosition : null,
    startedMatchId: status === 'STARTED' ? 'match-new' : null,
    participants: assignments,
    createdAt: '2026-09-06T01:00:00Z',
    startedAt: status === 'STARTED' ? '2026-09-06T01:05:00Z' : null,
    cancelledAt: status === 'CANCELLED' ? '2026-09-06T01:05:00Z' : null,
    updatedAt: '2026-09-06T01:00:00Z',
    version: 0,
  }
}

function withStatuses(
  participants: readonly SessionParticipantResponse[],
  statuses: readonly ParticipantStatus[],
) {
  return participants.map((participant, index) => ({
    ...participant,
    status: statuses[index] ?? participant.status,
    waitingSince:
      statuses[index] === 'WAITING' ? '2026-09-06T00:30:00Z' : null,
  }))
}

function renderQueue({
  courtIndex = 1,
  plans = [plan('plan-1', `session-court-${courtIndex + 1}`, 1)],
  statuses,
  sessionStatus = 'IN_PROGRESS',
}: {
  readonly courtIndex?: number
  readonly plans?: readonly MatchPlanResponse[]
  readonly statuses?: readonly ParticipantStatus[]
  readonly sessionStatus?: string
} = {}) {
  const input = createLiveSessionInput()
  const participants = statuses
    ? withStatuses(input.participants, statuses)
    : input.participants
  const model = composeLiveSessionModel({
    ...input,
    participants,
    matchPlans: plans,
  })
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: { retry: 3 },
      queries: { retry: false },
    },
  })
  queryClients.push(queryClient)
  vi.spyOn(queryClient, 'refetchQueries').mockResolvedValue(undefined)
  const court = model.courts[courtIndex]
  const participantViews = [
    ...model.waitingParticipants,
    ...model.registeredParticipants,
    ...model.pausedParticipants,
    ...model.playingParticipants,
    ...model.leftParticipants,
  ]
  render(
    <QueryClientProvider client={queryClient}>
      <MatchPlanQueue
        sessionId="session-1"
        sessionStatus={sessionStatus}
        court={court}
        courts={model.courts}
        participants={participantViews}
        matchPlans={plans}
      />
    </QueryClientProvider>,
  )
  return { queryClient, court, participants: participantViews }
}

async function selectFourPlayers(user: ReturnType<typeof userEvent.setup>) {
  const values = ['participant-1', 'participant-2', 'participant-3', 'participant-4']
  for (const [index, label] of [
    'Đội A — Vị trí 1',
    'Đội A — Vị trí 2',
    'Đội B — Vị trí 1',
    'Đội B — Vị trí 2',
  ].entries()) {
    await user.selectOptions(screen.getByRole('combobox', { name: label }), values[index])
  }
}

describe('MatchPlan Queue', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  afterEach(() => {
    queryClients.forEach((queryClient) => queryClient.clear())
    queryClients.length = 0
  })

  it.each([0, 1, 2])('allows planning on Session Court state #%s', (courtIndex) => {
    renderQueue({ courtIndex, plans: [] })
    expect(screen.getByRole('button', { name: 'Xếp trận tiếp theo' })).toBeEnabled()
  })

  it('shows only QUEUED plans in queuePosition order on a PLAYING Court', () => {
    renderQueue({
      courtIndex: 0,
      plans: [
        plan('second', 'session-court-1', 2),
        plan('cancelled', 'session-court-1', 1, 'CANCELLED'),
        plan('first', 'session-court-1', 1),
      ],
    })
    const cards = screen.getAllByRole('article')
    expect(cards).toHaveLength(2)
    expect(within(cards[0]).getByText('#1')).toBeVisible()
    expect(within(cards[1]).getByText('#2')).toBeVisible()
    expect(screen.getByText('Đang chờ sân trống')).toBeVisible()
  })

  it('keeps queued plans visible with a warning on an UNAVAILABLE Court', () => {
    renderQueue({ courtIndex: 2 })
    expect(screen.getByText('⚠ Sân đang tạm khóa')).toBeVisible()
    expect(screen.getByText('#1')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Đổi sân' })).toBeEnabled()
  })

  it('offers REGISTERED, WAITING, PLAYING and PAUSED participants but excludes LEFT', async () => {
    const user = userEvent.setup()
    const input = createLiveSessionInput()
    const leftParticipant = { ...input.participants[4], status: 'LEFT' as const }
    const modelInput = {
      ...input,
      participants: [...input.participants.slice(0, 4), leftParticipant],
      matchPlans: [],
    }
    const model = composeLiveSessionModel(modelInput)
    const queryClient = new QueryClient()
    queryClients.push(queryClient)
    render(
      <QueryClientProvider client={queryClient}>
        <MatchPlanQueue
          sessionId="session-1"
          sessionStatus="IN_PROGRESS"
          court={model.courts[1]}
          courts={model.courts}
          participants={[
            ...model.waitingParticipants,
            ...model.registeredParticipants,
            ...model.pausedParticipants,
            ...model.playingParticipants,
            ...model.leftParticipants,
          ]}
          matchPlans={[]}
        />
      </QueryClientProvider>,
    )
    await user.click(screen.getByRole('button', { name: 'Xếp trận tiếp theo' }))
    const options = within(screen.getByRole('combobox', { name: 'Đội A — Vị trí 1' }))
    expect(options.getByRole('option', { name: /An Nguyen.*Đang chờ/ })).toBeVisible()
    expect(options.getByRole('option', { name: /Chi Le.*Chưa điểm danh/ })).toBeVisible()
    expect(options.getByRole('option', { name: /Dung Pham.*Đang tạm nghỉ/ })).toBeVisible()
    expect(options.queryByRole('option', { name: /Giang Vo/ })).not.toBeInTheDocument()
  })

  it('creates with exactly four unique assignments and retry disabled', async () => {
    const user = userEvent.setup()
    createMock.mockResolvedValue(plan('created', 'session-court-2', 1))
    const { queryClient } = renderQueue({ plans: [] })
    await user.click(screen.getByRole('button', { name: 'Xếp trận tiếp theo' }))
    await selectFourPlayers(user)
    await user.click(screen.getByRole('button', { name: 'Xếp trận' }))
    await waitFor(() => expect(createMock).toHaveBeenCalledOnce())
    expect(createMock).toHaveBeenCalledWith('session-1', 'session-court-2', {
      participants: assignments.map(({ id: _id, ...assignment }) => assignment),
    })
    expect(queryClient.getMutationCache().getAll()[0]?.options.retry).toBe(false)
  })

  it('edits all four assignments through the backend', async () => {
    const user = userEvent.setup()
    const currentPlan = plan('plan-1', 'session-court-2', 1)
    updateMock.mockResolvedValue(currentPlan)
    renderQueue({ plans: [currentPlan] })
    await user.click(screen.getByRole('button', { name: 'Chỉnh' }))
    await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))
    await waitFor(() => expect(updateMock).toHaveBeenCalledOnce())
    expect(updateMock.mock.calls[0]?.[0]).toBe('plan-1')
    expect(updateMock.mock.calls[0]?.[1].participants).toHaveLength(4)
  })

  it('moves a plan to an UNAVAILABLE Court and reconciles authoritative plans', async () => {
    const user = userEvent.setup()
    const currentPlan = plan('plan-1', 'session-court-2', 1)
    moveMock.mockResolvedValue({ ...currentPlan, sessionCourtId: 'session-court-3' })
    const { queryClient } = renderQueue({ plans: [currentPlan] })
    await user.click(screen.getByRole('button', { name: 'Đổi sân' }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Chuyển sang sân' }), 'session-court-3')
    await user.click(screen.getByRole('button', { name: 'Chuyển sân' }))
    await waitFor(() => expect(moveMock).toHaveBeenCalledWith('plan-1', { targetSessionCourtId: 'session-court-3' }))
    expect(queryClient.refetchQueries).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: ['sessionMatchPlans', 'session-1'] }),
      { throwOnError: true },
    )
  })

  it('guards invalid reorder directions and sends the correct target position', async () => {
    const user = userEvent.setup()
    const plans = [plan('plan-1', 'session-court-2', 1), plan('plan-2', 'session-court-2', 2)]
    reorderMock.mockResolvedValue(plans[1])
    renderQueue({ plans })
    expect(screen.getByRole('button', { name: 'Đưa trận #1 lên' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Đưa trận #2 xuống' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Đưa trận #1 xuống' }))
    await waitFor(() => expect(reorderMock).toHaveBeenCalledWith('plan-1', { targetPosition: 2 }))
  })

  it('cancels only after backend success and plan reconciliation', async () => {
    const user = userEvent.setup()
    const currentPlan = plan('plan-1', 'session-court-2', 1)
    let resolveCancel: (value: MatchPlanResponse) => void = () => undefined
    cancelMock.mockReturnValue(new Promise((resolve) => { resolveCancel = resolve }))
    renderQueue({ plans: [currentPlan] })
    await user.click(screen.getByRole('button', { name: 'Hủy khỏi hàng chờ' }))
    await user.click(screen.getByRole('button', { name: 'Xác nhận hủy' }))
    expect(screen.getByText('#1')).toBeVisible()
    resolveCancel({ ...currentPlan, status: 'CANCELLED', queuePosition: null })
    await waitFor(() => expect(cancelMock).toHaveBeenCalledOnce())
  })

  it('starts only a ready queue head and reconciles all runtime resources', async () => {
    const user = userEvent.setup()
    const currentPlan = plan('plan-1', 'session-court-2', 1)
    startMock.mockResolvedValue({ matchPlan: { ...currentPlan, status: 'STARTED', queuePosition: null }, match: createLiveSessionInput().matches[0] })
    const { queryClient } = renderQueue({
      plans: [currentPlan],
      statuses: ['WAITING', 'WAITING', 'WAITING', 'WAITING'],
    })
    await user.click(screen.getByRole('button', { name: 'Bắt đầu trận' }))
    await waitFor(() => expect(startMock).toHaveBeenCalledWith('plan-1'))
    expect(vi.mocked(queryClient.refetchQueries).mock.calls.map(([filters]) => filters?.queryKey)).toEqual([
      ['session', 'session-1'],
      ['sessionMatchPlans', 'session-1'],
      ['sessionMatches', 'session-1'],
      ['sessionParticipants', 'session-1'],
      ['sessionCourts', 'session-1'],
    ])
  })

  it('blocks blind Create replay after an unknown response until Check again', async () => {
    const user = userEvent.setup()
    createMock.mockRejectedValue(new TypeError('Failed to fetch'))
    renderQueue({ plans: [] })
    await user.click(screen.getByRole('button', { name: 'Xếp trận tiếp theo' }))
    await selectFourPlayers(user)
    await user.click(screen.getByRole('button', { name: 'Xếp trận' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Kiểm tra lại' })).toBeVisible())
    expect(createMock).toHaveBeenCalledOnce()
  })

  it('derives concise Start-disabled reasons from authoritative runtime state', () => {
    const currentPlan = plan('plan-1', 'session-court-2', 1)
    const { court, participants } = renderQueue({ plans: [currentPlan] })
    const participantById = new Map(participants.map((participant) => [participant.sessionParticipantId, participant]))
    expect(matchPlanStartReason(currentPlan, court, 'IN_PROGRESS', participantById)).toBe('Có người chơi đang tạm nghỉ')
    expect(matchPlanStartReason({ ...currentPlan, queuePosition: 2 }, court, 'IN_PROGRESS', participantById)).toBe('Chờ đến lượt')
    expect(matchPlanStartReason(currentPlan, court, 'COMPLETED', participantById)).toBe('Phiên không còn diễn ra')
  })
})
