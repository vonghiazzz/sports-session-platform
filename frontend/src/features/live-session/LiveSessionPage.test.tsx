import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createLiveSessionInput } from '../../test/liveSessionFixtures'
import { LiveSessionScreen } from './LiveSessionPage'
import type { LiveSessionDataState } from './useLiveSessionData'
import type { ParticipantStatus } from '../../api/contracts'

const refresh = vi.fn(async () => undefined)
const now = new Date('2026-09-02T10:00:00Z')
const queryClients: QueryClient[] = []

function state(
  status: 'loading' | 'not-found' | 'error',
): LiveSessionDataState {
  return {
    status,
    refresh,
    isRefreshing: false,
  }
}

function renderScreen(state: LiveSessionDataState, currentTime = now) {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: { retry: false },
      queries: { retry: false },
    },
  })
  queryClients.push(queryClient)

  return render(
    <QueryClientProvider client={queryClient}>
      <LiveSessionScreen state={state} now={currentTime} />
    </QueryClientProvider>,
  )
}

function readyState(): LiveSessionDataState {
  return {
    status: 'ready',
    data: createLiveSessionInput(),
    refresh,
    isRefreshing: false,
  }
}

describe('LiveSessionScreen', () => {
  beforeEach(() => {
    refresh.mockClear()
  })

  afterEach(() => {
    queryClients.forEach((queryClient) => queryClient.clear())
    queryClients.length = 0
  })

  it('renders a distinct initial loading state', () => {
    renderScreen(state('loading'))

    expect(screen.getByRole('heading', { name: 'Đang tải phiên…' })).toBeVisible()
    expect(screen.getByText('Đang tải sân, người chơi và trận đấu.')).toBeVisible()
  })

  it('renders a clear Session not found state', () => {
    renderScreen(state('not-found'))

    expect(screen.getByRole('heading', { name: 'Không tìm thấy phiên' })).toBeVisible()
  })

  it('renders an essential-read error separately from empty data', () => {
    renderScreen(state('error'))

    expect(
      screen.getByRole('heading', {
        name: 'Không thể tải dữ liệu phiên trực tiếp.',
      }),
    ).toBeVisible()
    expect(screen.getByRole('button', { name: 'Thử lại' })).toBeEnabled()
  })

  it('keeps rendered data visible when a background refresh fails', () => {
    const currentState = readyState()
    if (currentState.status !== 'ready') {
      throw new Error('Expected ready fixture data')
    }

    renderScreen({ ...currentState, hasBackgroundError: true })

    expect(
      screen.getByRole('heading', { name: 'Wednesday Badminton' }),
    ).toBeVisible()
    expect(
      screen.getByText(/Dữ liệu gần nhất vẫn được giữ lại/),
    ).toBeVisible()
    expect(
      screen.queryByRole('heading', {
        name: 'Không thể tải dữ liệu phiên trực tiếp.',
      }),
    ).not.toBeInTheDocument()
  })

  it('renders realistic multi-Court and multi-Participant data read-only', () => {
    const { now: fixtureNow, ...data } = createLiveSessionInput()
    const readyState: LiveSessionDataState = {
      status: 'ready',
      data,
      refresh,
      isRefreshing: false,
    }

    renderScreen(readyState, fixtureNow)

    expect(
      screen.getByRole('heading', { name: 'Wednesday Badminton' }),
    ).toBeVisible()
    expect(screen.getByText('Riverside Sports Hall · District 2')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Court One' })).toBeVisible()
    expect(screen.getAllByRole('heading', { name: 'Court Two' })).toHaveLength(2)
    expect(screen.getByRole('heading', { name: 'Court Three' })).toBeVisible()
    expect(screen.getAllByText('An Nguyen')).toHaveLength(2)
    expect(screen.getByText('Chờ 30 phút')).toBeVisible()
    expect(screen.getByText('Đã tạo — chưa bắt đầu')).toBeVisible()
    expect(screen.getByText(/16:00 02\/09\/2026/)).toBeVisible()
    expect(screen.getByText(/19:00 02\/09\/2026/)).toBeVisible()
    expect(screen.getByText('16:05 02/09/2026')).toBeVisible()
    expect(screen.getByText('Tạo lúc 16:40 02/09/2026')).toBeVisible()
    expect(screen.getByText('16:45 02/09/2026')).toBeVisible()
    expect(screen.getByText('Đang diễn ra')).toBeVisible()
    expect(screen.getByText('Cầu lông')).toBeVisible()
    expect(screen.getByText('Đánh đôi')).toBeVisible()
    expect(screen.getAllByText('Thủ công')).toHaveLength(2)
    expect(screen.getByText('Sẵn sàng')).toBeVisible()
    expect(screen.getByText('Tạm khóa')).toBeVisible()
    expect(screen.queryByText('IN_PROGRESS')).not.toBeInTheDocument()
    expect(screen.queryByText('INTERMEDIATE_MINUS')).not.toBeInTheDocument()
    expect(screen.queryByText('AVAILABLE')).not.toBeInTheDocument()
    expect(screen.queryByText(/reserved/i)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Làm mới' })).toBeEnabled()
  })

  it('renders People groups in operational priority order with counts', () => {
    renderScreen(readyState())
    const panel = screen.getByRole('heading', { name: 'Người chơi' }).closest('section')
    expect(panel).not.toBeNull()
    const people = within(panel as HTMLElement)

    expect(
      people.getAllByRole('heading', { level: 3 }).map((heading) => heading.textContent),
    ).toEqual(['Đang chờ', 'Đang chơi', 'Đã đăng ký', 'Tạm nghỉ', 'Đã rời'])
    expect(people.getByText('8 người chơi')).toBeVisible()
    expect(people.getByRole('region', { name: 'Đang chờ: 2 người' })).toBeVisible()
    expect(people.getByRole('region', { name: 'Đang chơi: 4 người' })).toBeVisible()
    expect(people.getByRole('region', { name: 'Đã đăng ký: 1 người' })).toBeVisible()
    expect(people.getByRole('region', { name: 'Tạm nghỉ: 1 người' })).toBeVisible()
    expect(people.getByRole('region', { name: 'Đã rời: 0 người' })).toBeVisible()
    expect(people.getAllByText('An Nguyen')).toHaveLength(1)
  })

  it('filters current Participants across groups case- and diacritic-insensitively', async () => {
    const user = userEvent.setup()
    const currentState = readyState()
    if (currentState.status !== 'ready') {
      throw new Error('Expected ready fixture data')
    }
    renderScreen({
      ...currentState,
      data: {
        ...currentState.data,
        players: currentState.data.players.map((player) =>
          player.id === 'player-1'
            ? { ...player, displayName: 'Nguyễn Nghĩa' }
            : player.id === 'player-4'
              ? { ...player, displayName: 'Nghĩa Tạm Nghỉ' }
              : player,
        ),
      },
    })

    await user.type(screen.getByLabelText('Tìm người chơi'), 'NGHIA')

    expect(screen.getByRole('region', { name: 'Đang chờ: 1 người' })).toBeVisible()
    expect(screen.getByRole('region', { name: 'Tạm nghỉ: 1 người' })).toBeVisible()
    expect(screen.queryByRole('region', { name: /Đang chơi:/ })).not.toBeInTheDocument()
    expect(
      within(screen.getByRole('region', { name: 'Đang chờ: 1 người' })).getByText(
        'Nguyễn Nghĩa',
      ),
    ).toBeVisible()
    expect(
      within(screen.getByRole('region', { name: 'Tạm nghỉ: 1 người' })).getByText(
        'Nghĩa Tạm Nghỉ',
      ),
    ).toBeVisible()
  })

  it('shows a Session-scoped empty state when People search has no match', async () => {
    const user = userEvent.setup()
    renderScreen(readyState())

    await user.type(screen.getByLabelText('Tìm người chơi'), 'không tồn tại')

    expect(screen.getByText('Không tìm thấy người chơi trong phiên.')).toBeVisible()
  })

  it('renders a representative 25-Participant Session with exact group counts', () => {
    const currentState = readyState()
    if (currentState.status !== 'ready') {
      throw new Error('Expected ready fixture data')
    }
    const playerTemplate = currentState.data.players[0]
    const participantTemplate = currentState.data.participants[0]
    const statuses: ParticipantStatus[] = [
      ...Array<ParticipantStatus>(8).fill('WAITING'),
      ...Array<ParticipantStatus>(8).fill('PLAYING'),
      ...Array<ParticipantStatus>(4).fill('REGISTERED'),
      ...Array<ParticipantStatus>(3).fill('PAUSED'),
      ...Array<ParticipantStatus>(2).fill('LEFT'),
    ]
    const players = statuses.map((_, index) => ({
      ...playerTemplate,
      id: `scale-player-${index}`,
      displayName: `Người chơi ${index + 1}`,
    }))
    const participants = statuses.map((status, index) => ({
      ...participantTemplate,
      id: `scale-participant-${index}`,
      playerId: `scale-player-${index}`,
      status,
      waitingSince:
        status === 'WAITING'
          ? `2026-09-02T09:${String(index).padStart(2, '0')}:00Z`
          : null,
    }))
    renderScreen({
      ...currentState,
      data: { ...currentState.data, players, participants, matches: [] },
    })

    const panel = screen.getByRole('heading', { name: 'Người chơi' }).closest('section')
    expect(panel).not.toBeNull()
    const people = within(panel as HTMLElement)
    expect(people.getByText('25 người chơi')).toBeVisible()
    expect(people.getByRole('region', { name: 'Đang chờ: 8 người' })).toBeVisible()
    expect(people.getByRole('region', { name: 'Đang chơi: 8 người' })).toBeVisible()
    expect(people.getByRole('region', { name: 'Đã đăng ký: 4 người' })).toBeVisible()
    expect(people.getByRole('region', { name: 'Tạm nghỉ: 3 người' })).toBeVisible()
    expect(people.getByRole('region', { name: 'Đã rời: 2 người' })).toBeVisible()
  })

  it('uses the single manual Refresh control for read refresh', async () => {
    const user = userEvent.setup()
    const { now: fixtureNow, ...data } = createLiveSessionInput()
    const readyState: LiveSessionDataState = {
      status: 'ready',
      data,
      refresh,
      isRefreshing: false,
    }
    renderScreen(readyState, fixtureNow)

    await user.click(screen.getByRole('button', { name: 'Làm mới' }))

    expect(refresh).toHaveBeenCalledOnce()
  })

  it('offers Participant actions from authoritative runtime states', () => {
    renderScreen(readyState())
    const heading = screen.getByRole('heading', { name: 'Người chơi' })
    const participantPanel = heading.closest('section')

    expect(participantPanel).not.toBeNull()
    const panel = within(participantPanel as HTMLElement)
    expect(panel.getByRole('button', { name: 'Điểm danh' })).toBeEnabled()
    expect(panel.getAllByRole('button', { name: 'Tạm nghỉ' })).toHaveLength(2)
    expect(panel.getByRole('button', { name: 'Trở lại' })).toBeEnabled()
    expect(panel.getAllByRole('button', { name: 'Rời phiên' })).toHaveLength(4)
  })

  it('does not offer Check In before the Session is in progress', () => {
    const input = createLiveSessionInput()
    const registered = input.participants.find(
      (participant) => participant.status === 'REGISTERED',
    )
    expect(registered).toBeDefined()
    const plannedState = readyState()
    if (plannedState.status !== 'ready' || registered === undefined) {
      throw new Error('Expected ready fixture data')
    }

    renderScreen({
      ...plannedState,
      data: {
        ...plannedState.data,
        session: {
          ...plannedState.data.session,
          status: 'PLANNED',
          startedAt: null,
        },
        participants: [registered],
      },
    })

    const heading = screen.getByRole('heading', { name: 'Người chơi' })
    const participantPanel = heading.closest('section')
    expect(participantPanel).not.toBeNull()
    const panel = within(participantPanel as HTMLElement)
    expect(panel.queryByRole('button', { name: 'Điểm danh' })).not.toBeInTheDocument()
    expect(panel.getByRole('button', { name: 'Rời phiên' })).toBeEnabled()
    expect(
      panel.getByText('Phiên phải đang diễn ra để điểm danh.'),
    ).toBeVisible()
  })

  it('offers no Participant action for PLAYING or LEFT', () => {
    const input = createLiveSessionInput()
    const playing = input.participants.find(
      (participant) => participant.status === 'PLAYING',
    )
    const waiting = input.participants.find(
      (participant) => participant.status === 'WAITING',
    )
    const currentState = readyState()
    if (
      currentState.status !== 'ready' ||
      playing === undefined ||
      waiting === undefined
    ) {
      throw new Error('Expected ready fixture data')
    }

    renderScreen({
      ...currentState,
      data: {
        ...currentState.data,
        participants: [
          playing,
          {
            ...waiting,
            status: 'LEFT',
            waitingSince: null,
            leftAt: '2026-09-02T09:55:00Z',
          },
        ],
      },
    })

    const heading = screen.getByRole('heading', { name: 'Người chơi' })
    const participantPanel = heading.closest('section')
    expect(participantPanel).not.toBeNull()
    const panel = within(participantPanel as HTMLElement)
    expect(
      panel.queryByRole('button', {
        name: /Điểm danh|Tạm nghỉ|Trở lại|Rời phiên/,
      }),
    ).not.toBeInTheDocument()
    expect(panel.getByRole('button', { name: '+ Thêm người chơi' })).toBeEnabled()
    expect(panel.getByRole('region', { name: 'Đã rời: 1 người' })).toBeVisible()
  })

  it('offers only status-valid Court actions', () => {
    renderScreen(readyState())
    const boardHeading = screen.getByRole('heading', { name: 'Bảng sân' })
    const board = boardHeading.closest('section')
    expect(board).not.toBeNull()
    const boardQueries = within(board as HTMLElement)

    const playingCard = boardQueries
      .getByRole('heading', { name: 'Court One' })
      .closest('article')
    const availableCard = boardQueries
      .getByRole('heading', { name: 'Court Two' })
      .closest('article')
    const unavailableCard = boardQueries
      .getByRole('heading', { name: 'Court Three' })
      .closest('article')
    expect(playingCard).not.toBeNull()
    expect(availableCard).not.toBeNull()
    expect(unavailableCard).not.toBeNull()

    expect(
      within(playingCard as HTMLElement).queryByRole('button', {
        name: /Mở sân|Tạm khóa sân|Tạo đề xuất/,
      }),
    ).not.toBeInTheDocument()
    expect(
      within(availableCard as HTMLElement).getByRole('button', {
        name: 'Tạm khóa sân',
      }),
    ).toBeEnabled()
    expect(
      within(availableCard as HTMLElement).getByRole('button', {
        name: 'Tạo đề xuất',
      }),
    ).toBeEnabled()
    expect(
      within(unavailableCard as HTMLElement).getByRole('button', {
        name: 'Mở sân',
      }),
    ).toBeEnabled()
    expect(
      within(unavailableCard as HTMLElement).queryByRole('button', {
        name: 'Tạo đề xuất',
      }),
    ).not.toBeInTheDocument()
  })

  it('shows Create and Start controls only while the Session is in progress', () => {
    renderScreen(readyState())

    expect(screen.getByRole('button', { name: 'Tạo trận' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Bắt đầu trận' })).toBeEnabled()
    expect(
      screen.getByText(/chưa giữ sân hoặc người chơi/i),
    ).toBeVisible()
  })

  it.each(['PLANNED', 'COMPLETED', 'CANCELLED'] as const)(
    'keeps Manual Match controls inactive for a %s Session',
    (sessionStatus) => {
      const currentState = readyState()
      if (currentState.status !== 'ready') {
        throw new Error('Expected ready fixture data')
      }

      renderScreen({
        ...currentState,
        data: {
          ...currentState.data,
          session: {
            ...currentState.data.session,
            status: sessionStatus,
          },
        },
      })

      expect(screen.queryByRole('button', { name: 'Tạo trận' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Bắt đầu trận' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Tạo đề xuất' })).not.toBeInTheDocument()
      const createdMatches = screen
        .getByRole('heading', { name: 'Trận chờ bắt đầu' })
        .closest('section')
      expect(createdMatches).not.toBeNull()
      expect(
        within(createdMatches as HTMLElement).getByRole('button', {
          name: 'Hủy trận',
        }),
      ).toBeEnabled()
      expect(
        screen.getByText(
          'Chỉ có thể tạo trận thủ công khi phiên đang diễn ra.',
        ),
      ).toBeVisible()
      expect(
        screen.getByText(
          'Trận này chỉ có thể bắt đầu khi phiên đang diễn ra.',
        ),
      ).toBeVisible()
    },
  )

  it.each(['PLAYING', 'COMPLETED', 'CANCELLED'] as const)(
    'does not offer Start for a %s Match',
    (matchStatus) => {
      const currentState = readyState()
      if (currentState.status !== 'ready') {
        throw new Error('Expected ready fixture data')
      }
      const createdMatch = currentState.data.matches.find(
        (match) => match.status === 'CREATED',
      )
      if (createdMatch === undefined) {
        throw new Error('Expected a CREATED Match fixture')
      }

      renderScreen({
        ...currentState,
        data: {
          ...currentState.data,
          matches: [{ ...createdMatch, status: matchStatus }],
        },
      })

      expect(screen.queryByRole('button', { name: 'Bắt đầu trận' })).not.toBeInTheDocument()
      if (matchStatus === 'PLAYING') {
        expect(screen.getByRole('button', { name: 'Kết thúc trận' })).toBeEnabled()
        expect(screen.getByRole('button', { name: 'Hủy trận' })).toBeEnabled()
      } else {
        expect(screen.queryByRole('button', { name: 'Kết thúc trận' })).not.toBeInTheDocument()
        expect(screen.queryByRole('button', { name: 'Hủy trận' })).not.toBeInTheDocument()
      }
    },
  )

  it('does not restrict Start by Match source', () => {
    const currentState = readyState()
    if (currentState.status !== 'ready') {
      throw new Error('Expected ready fixture data')
    }

    renderScreen({
      ...currentState,
      data: {
        ...currentState.data,
        matches: currentState.data.matches.map((match) =>
          match.status === 'CREATED'
            ? { ...match, source: 'RECOMMENDATION' }
            : match,
        ),
      },
    })

    expect(screen.getByRole('button', { name: 'Bắt đầu trận' })).toBeEnabled()
  })

  it('keeps Complete and Cancel actionable for a PLAYING Match after Session cancellation', () => {
    const currentState = readyState()
    if (currentState.status !== 'ready') {
      throw new Error('Expected ready fixture data')
    }

    renderScreen({
      ...currentState,
      data: {
        ...currentState.data,
        session: { ...currentState.data.session, status: 'CANCELLED' },
        matches: currentState.data.matches.filter(
          (match) => match.status === 'PLAYING',
        ),
      },
    })

    expect(screen.getByRole('button', { name: 'Kết thúc trận' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Hủy trận' })).toBeEnabled()
    const peoplePanel = screen
      .getByRole('heading', { name: 'Người chơi' })
      .closest('section')
    expect(peoplePanel).not.toBeNull()
    const people = within(peoplePanel as HTMLElement)
    expect(people.getByRole('region', { name: 'Đang chơi: 4 người' })).toBeVisible()
    expect(people.getByText('Giang Vo')).toBeVisible()
    expect(
      people.queryByRole('button', {
        name: /Điểm danh|Tạm nghỉ|Trở lại|Rời phiên|\+ Thêm người chơi/,
      }),
    ).not.toBeInTheDocument()
  })
})
