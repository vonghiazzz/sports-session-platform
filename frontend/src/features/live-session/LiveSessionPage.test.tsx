import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createLiveSessionInput } from '../../test/liveSessionFixtures'
import { LiveSessionScreen } from './LiveSessionPage'
import type { LiveSessionDataState } from './useLiveSessionData'

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
    expect(screen.getByText('30 phút')).toBeVisible()
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
    expect(panel.getByText('1 người đã rời phiên')).toBeVisible()
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
        name: /Mở sân|Tạm khóa sân/,
      }),
    ).not.toBeInTheDocument()
    expect(
      within(availableCard as HTMLElement).getByRole('button', {
        name: 'Tạm khóa sân',
      }),
    ).toBeEnabled()
    expect(
      within(unavailableCard as HTMLElement).getByRole('button', {
        name: 'Mở sân',
      }),
    ).toBeEnabled()
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
  })
})
