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

    expect(screen.getByRole('heading', { name: 'Loading Session…' })).toBeVisible()
    expect(screen.getByText('Gathering Courts, Players, and Matches.')).toBeVisible()
  })

  it('renders a clear Session not found state', () => {
    renderScreen(state('not-found'))

    expect(screen.getByRole('heading', { name: 'Session not found' })).toBeVisible()
  })

  it('renders an essential-read error separately from empty data', () => {
    renderScreen(state('error'))

    expect(
      screen.getByRole('heading', {
        name: 'Unable to load live Session data.',
      }),
    ).toBeVisible()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeEnabled()
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
    expect(screen.getByText('30 min')).toBeVisible()
    expect(screen.getByText('Created — not started')).toBeVisible()
    expect(screen.queryByText(/reserved/i)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Refresh' })).toBeEnabled()
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

    await user.click(screen.getByRole('button', { name: 'Refresh' }))

    expect(refresh).toHaveBeenCalledOnce()
  })

  it('offers Participant actions from authoritative runtime states', () => {
    renderScreen(readyState())
    const heading = screen.getByRole('heading', { name: 'Participants' })
    const participantPanel = heading.closest('section')

    expect(participantPanel).not.toBeNull()
    const panel = within(participantPanel as HTMLElement)
    expect(panel.getByRole('button', { name: 'Check In' })).toBeEnabled()
    expect(panel.getAllByRole('button', { name: 'Pause' })).toHaveLength(2)
    expect(panel.getByRole('button', { name: 'Resume' })).toBeEnabled()
    expect(panel.getAllByRole('button', { name: 'Leave' })).toHaveLength(4)
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

    const heading = screen.getByRole('heading', { name: 'Participants' })
    const participantPanel = heading.closest('section')
    expect(participantPanel).not.toBeNull()
    const panel = within(participantPanel as HTMLElement)
    expect(panel.queryByRole('button', { name: 'Check In' })).not.toBeInTheDocument()
    expect(panel.getByRole('button', { name: 'Leave' })).toBeEnabled()
    expect(
      panel.getByText('Session must be in progress to check in.'),
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

    const heading = screen.getByRole('heading', { name: 'Participants' })
    const participantPanel = heading.closest('section')
    expect(participantPanel).not.toBeNull()
    const panel = within(participantPanel as HTMLElement)
    expect(panel.queryByRole('button')).not.toBeInTheDocument()
    expect(panel.getByText('1 left this Session')).toBeVisible()
  })

  it('offers only status-valid Court actions', () => {
    renderScreen(readyState())
    const boardHeading = screen.getByRole('heading', { name: 'Court Board' })
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
        name: /Enable Court|Disable Court/,
      }),
    ).not.toBeInTheDocument()
    expect(
      within(availableCard as HTMLElement).getByRole('button', {
        name: 'Disable Court',
      }),
    ).toBeEnabled()
    expect(
      within(unavailableCard as HTMLElement).getByRole('button', {
        name: 'Enable Court',
      }),
    ).toBeEnabled()
  })

  it('shows Create and Start controls only while the Session is in progress', () => {
    renderScreen(readyState())

    expect(screen.getByRole('button', { name: 'Create Match' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Start Match' })).toBeEnabled()
    expect(
      screen.getByText(/does not reserve its Court or players/i),
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

      expect(screen.queryByRole('button', { name: 'Create Match' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Start Match' })).not.toBeInTheDocument()
      const createdMatches = screen
        .getByRole('heading', { name: 'Created Matches' })
        .closest('section')
      expect(createdMatches).not.toBeNull()
      expect(
        within(createdMatches as HTMLElement).getByRole('button', {
          name: 'Cancel Match',
        }),
      ).toBeEnabled()
      expect(
        screen.getByText(
          'Manual Matches can only be created while the Session is in progress.',
        ),
      ).toBeVisible()
      expect(
        screen.getByText(
          'This Match can only start while the Session is in progress.',
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

      expect(screen.queryByRole('button', { name: 'Start Match' })).not.toBeInTheDocument()
      if (matchStatus === 'PLAYING') {
        expect(screen.getByRole('button', { name: 'Complete Match' })).toBeEnabled()
        expect(screen.getByRole('button', { name: 'Cancel Match' })).toBeEnabled()
      } else {
        expect(screen.queryByRole('button', { name: 'Complete Match' })).not.toBeInTheDocument()
        expect(screen.queryByRole('button', { name: 'Cancel Match' })).not.toBeInTheDocument()
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

    expect(screen.getByRole('button', { name: 'Start Match' })).toBeEnabled()
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

    expect(screen.getByRole('button', { name: 'Complete Match' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Cancel Match' })).toBeEnabled()
  })
})
