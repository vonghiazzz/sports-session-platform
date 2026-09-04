import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PropsWithChildren } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { SessionResponse, SessionStatus } from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  cancelSession,
  completeSession,
  getPlayers,
  getSession,
  getSessionCourts,
  getSessionMatches,
  getSessionParticipants,
  getVenue,
  getVenueCourts,
} from '../../api/liveSessionApi'
import { createLiveSessionInput } from '../../test/liveSessionFixtures'
import { LiveSessionScreen } from './LiveSessionPage'
import { useLiveSessionData } from './useLiveSessionData'

vi.mock('../../api/liveSessionApi', () => ({
  cancelMatch: vi.fn(),
  cancelSession: vi.fn(),
  checkInParticipant: vi.fn(),
  completeMatch: vi.fn(),
  completeSession: vi.fn(),
  createManualMatch: vi.fn(),
  disableSessionCourt: vi.fn(),
  enableSessionCourt: vi.fn(),
  getPlayers: vi.fn(),
  getSession: vi.fn(),
  getSessionCourts: vi.fn(),
  getSessionMatches: vi.fn(),
  getSessionParticipants: vi.fn(),
  getVenue: vi.fn(),
  getVenueCourts: vi.fn(),
  leaveParticipant: vi.fn(),
  pauseParticipant: vi.fn(),
  resumeParticipant: vi.fn(),
  startMatch: vi.fn(),
}))

const SESSION_ID = 'session-1'
const NOW = new Date('2026-09-02T10:00:00Z')
const queryClients: QueryClient[] = []

const cancelSessionMock = vi.mocked(cancelSession)
const completeSessionMock = vi.mocked(completeSession)
const getPlayersMock = vi.mocked(getPlayers)
const getSessionMock = vi.mocked(getSession)
const getSessionCourtsMock = vi.mocked(getSessionCourts)
const getSessionMatchesMock = vi.mocked(getSessionMatches)
const getSessionParticipantsMock = vi.mocked(getSessionParticipants)
const getVenueMock = vi.mocked(getVenue)
const getVenueCourtsMock = vi.mocked(getVenueCourts)

function deferred<T>() {
  let resolvePromise: (value: T | PromiseLike<T>) => void = () => {
    throw new Error('Deferred promise resolver is unavailable')
  }
  const promise = new Promise<T>((resolve) => {
    resolvePromise = resolve
  })
  return { promise, resolve: resolvePromise }
}

function sessionWithStatus(
  session: SessionResponse,
  status: SessionStatus,
): SessionResponse {
  return {
    ...session,
    status,
    startedAt: status === 'PLANNED' ? null : session.startedAt,
    completedAt:
      status === 'COMPLETED' ? '2026-09-02T10:05:00Z' : null,
    cancelledAt:
      status === 'CANCELLED' ? '2026-09-02T10:05:00Z' : null,
    version: session.version + 1,
  }
}

function arrangeReadSuccess({
  session,
  matches,
}: {
  readonly session?: SessionResponse
  readonly matches?: ReturnType<typeof createLiveSessionInput>['matches']
} = {}) {
  const input = createLiveSessionInput()
  getSessionMock.mockResolvedValue(session ?? input.session)
  getSessionParticipantsMock.mockResolvedValue(input.participants)
  getSessionCourtsMock.mockResolvedValue(input.sessionCourts)
  getPlayersMock.mockResolvedValue(input.players)
  getSessionMatchesMock.mockResolvedValue(matches ?? input.matches)
  getVenueMock.mockResolvedValue(input.venue)
  getVenueCourtsMock.mockResolvedValue(input.venueCourts)
  return input
}

function renderControlRoom() {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: { retry: 3 },
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

  function Harness() {
    const state = useLiveSessionData(SESSION_ID)
    return <LiveSessionScreen state={state} now={NOW} />
  }

  return render(<Harness />, { wrapper: Wrapper })
}

function sessionHeader() {
  const header = screen
    .getByRole('heading', { name: 'Wednesday Badminton' })
    .closest('header')
  if (header === null) {
    throw new Error('Expected the Session header')
  }
  return header
}

function createdMatchCard() {
  const createdMatches = screen
    .getByRole('heading', { name: 'Created Matches' })
    .closest('section')
  const card = createdMatches
    ? within(createdMatches).getByRole('heading', { name: 'Court Two' }).closest('article')
    : null
  if (card === null) {
    throw new Error('Expected a CREATED Match card')
  }
  return card
}

async function confirmComplete(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: 'Complete Session' }))
  await user.click(screen.getByRole('button', { name: 'Confirm Complete' }))
}

async function confirmCancel(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: 'Cancel Session' }))
  await user.click(screen.getByRole('button', { name: 'Confirm Cancel' }))
}

async function expectRuntimeReadsTwice() {
  await waitFor(() => {
    expect(getSessionMock).toHaveBeenCalledTimes(2)
    expect(getSessionMatchesMock).toHaveBeenCalledTimes(2)
    expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2)
    expect(getSessionCourtsMock).toHaveBeenCalledTimes(2)
  })
  expect(getPlayersMock).toHaveBeenCalledTimes(1)
  expect(getVenueMock).toHaveBeenCalledTimes(1)
  expect(getVenueCourtsMock).toHaveBeenCalledTimes(1)
}

beforeEach(() => {
  vi.resetAllMocks()
})

afterEach(() => {
  queryClients.forEach((queryClient) => queryClient.clear())
  queryClients.length = 0
})

describe('Session lifecycle action matrix', () => {
  it('offers Cancel but not Complete for a PLANNED Session', async () => {
    const input = createLiveSessionInput()
    arrangeReadSuccess({
      session: sessionWithStatus(input.session, 'PLANNED'),
      matches: input.matches.filter((match) => match.status !== 'PLAYING'),
    })
    renderControlRoom()

    expect(
      await screen.findByRole('button', { name: 'Cancel Session' }),
    ).toBeEnabled()
    expect(
      screen.queryByRole('button', { name: 'Complete Session' }),
    ).not.toBeInTheDocument()
  })

  it('offers Complete and Cancel for an IN_PROGRESS Session without a PLAYING Match', async () => {
    const input = arrangeReadSuccess()
    getSessionMatchesMock.mockResolvedValue(
      input.matches.filter((match) => match.status !== 'PLAYING'),
    )
    renderControlRoom()

    expect(
      await screen.findByRole('button', { name: 'Complete Session' }),
    ).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Cancel Session' })).toBeEnabled()
  })

  it.each(['COMPLETED', 'CANCELLED'] as const)(
    'offers no Session terminal action for a %s Session',
    async (status) => {
      const input = createLiveSessionInput()
      arrangeReadSuccess({ session: sessionWithStatus(input.session, status) })
      renderControlRoom()

      await screen.findByRole('heading', { name: 'Wednesday Badminton' })
      expect(
        screen.queryByRole('heading', { name: 'End Session' }),
      ).not.toBeInTheDocument()
      expect(
        screen.queryByRole('button', { name: 'Complete Session' }),
      ).not.toBeInTheDocument()
      expect(
        screen.queryByRole('button', { name: 'Cancel Session' }),
      ).not.toBeInTheDocument()
    },
  )

  it('blocks Complete with an explanation but keeps Cancel available for a PLAYING Match', async () => {
    arrangeReadSuccess()
    renderControlRoom()

    expect(
      await screen.findByRole('button', { name: 'Complete Session' }),
    ).toBeDisabled()
    expect(
      screen.getByText(/unavailable while a Match is PLAYING/i),
    ).toBeVisible()
    expect(screen.getByRole('button', { name: 'Cancel Session' })).toBeEnabled()
    expect(completeSessionMock).not.toHaveBeenCalled()
  })
})

describe('Session lifecycle authoritative reconciliation', () => {
  it('completes from refreshed GET while preserving a CREATED Match recovery path', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const createdMatches = input.matches.filter(
      (match) => match.status === 'CREATED',
    )
    const completedSession = sessionWithStatus(input.session, 'COMPLETED')
    getSessionMock
      .mockResolvedValueOnce(input.session)
      .mockResolvedValue(completedSession)
    getSessionMatchesMock.mockResolvedValue(createdMatches)
    completeSessionMock.mockResolvedValue(completedSession)
    renderControlRoom()

    await screen.findByRole('button', { name: 'Complete Session' })
    await user.click(screen.getByRole('button', { name: 'Complete Session' }))
    expect(
      screen.getByText(/terminal action cannot be undone/i),
    ).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Confirm Complete' }))

    await expectRuntimeReadsTwice()
    expect(completeSessionMock).toHaveBeenCalledOnce()
    expect(completeSessionMock).toHaveBeenCalledWith(SESSION_ID)
    expect(within(sessionHeader()).getByText('Completed')).toBeVisible()
    const matchCard = createdMatchCard()
    expect(
      within(matchCard).queryByRole('button', { name: 'Start Match' }),
    ).not.toBeInTheDocument()
    expect(
      within(matchCard).getByRole('button', { name: 'Cancel Match' }),
    ).toBeEnabled()
  })

  it('cancels from refreshed GET without releasing a PLAYING Match, Court, or Participants', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const cancelledSession = sessionWithStatus(input.session, 'CANCELLED')
    getSessionMock
      .mockResolvedValueOnce(input.session)
      .mockResolvedValue(cancelledSession)
    cancelSessionMock.mockResolvedValue(cancelledSession)
    renderControlRoom()

    await screen.findByRole('button', { name: 'Cancel Session' })
    await user.click(screen.getByRole('button', { name: 'Cancel Session' }))
    expect(
      screen.getByText(/Playing Matches will not be resolved automatically/i),
    ).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Confirm Cancel' }))

    await expectRuntimeReadsTwice()
    expect(cancelSessionMock).toHaveBeenCalledOnce()
    expect(within(sessionHeader()).getByText('Cancelled')).toBeVisible()
    const playingCourt = screen
      .getByRole('heading', { name: 'Court Board' })
      .closest('section')
    expect(playingCourt).not.toBeNull()
    const courtOne = within(playingCourt as HTMLElement)
      .getByRole('heading', { name: 'Court One' })
      .closest('article')
    expect(courtOne).not.toBeNull()
    expect(within(courtOne as HTMLElement).getByText('PLAYING')).toBeVisible()
    expect(
      within(courtOne as HTMLElement).getByRole('button', {
        name: 'Complete Match',
      }),
    ).toBeEnabled()
    expect(
      within(courtOne as HTMLElement).getByRole('button', {
        name: 'Cancel Match',
      }),
    ).toBeEnabled()
    const playingParticipants = screen
      .getByRole('heading', { name: 'Playing' })
      .closest('section')
    expect(playingParticipants).not.toBeNull()
    expect(playingParticipants).toHaveTextContent('Giang Vo')
    expect(playingParticipants).toHaveTextContent('Linh Ho')
  })

  it.each([
    ['COMPLETE', 'COMPLETED'],
    ['CANCEL', 'CANCELLED'],
  ] as const)(
    'does not trust a %s POST response when refreshed GET remains IN_PROGRESS',
    async (action, responseStatus) => {
      const user = userEvent.setup()
      const input = arrangeReadSuccess()
      const matches = input.matches.filter(
        (match) => match.status !== 'PLAYING',
      )
      getSessionMatchesMock.mockResolvedValue(matches)
      const response = sessionWithStatus(input.session, responseStatus)
      if (action === 'COMPLETE') {
        completeSessionMock.mockResolvedValue(response)
      } else {
        cancelSessionMock.mockResolvedValue(response)
      }
      renderControlRoom()

      await screen.findByRole('button', {
        name: action === 'COMPLETE' ? 'Complete Session' : 'Cancel Session',
      })
      if (action === 'COMPLETE') {
        await confirmComplete(user)
      } else {
        await confirmCancel(user)
      }

      await expectRuntimeReadsTwice()
      expect(within(sessionHeader()).getByText('In progress')).toBeVisible()
      expect(
        screen.queryByText(responseStatus === 'COMPLETED' ? 'Completed' : 'Cancelled'),
      ).not.toBeInTheDocument()
    },
  )
})

describe('Session lifecycle pending and failure safety', () => {
  it('keeps GET state and blocks duplicate or competing commands while Complete is pending', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    getSessionMatchesMock.mockResolvedValue(
      input.matches.filter((match) => match.status !== 'PLAYING'),
    )
    const request = deferred<SessionResponse>()
    completeSessionMock.mockReturnValue(request.promise)
    renderControlRoom()

    await screen.findByRole('button', { name: 'Complete Session' })
    await user.click(screen.getByRole('button', { name: 'Complete Session' }))
    const confirmButton = screen.getByRole('button', { name: 'Confirm Complete' })
    fireEvent.click(confirmButton)
    fireEvent.click(confirmButton)

    expect(await screen.findByRole('button', { name: 'Completing…' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel Session' })).toBeDisabled()
    expect(within(sessionHeader()).getByText('In progress')).toBeVisible()
    expect(completeSessionMock).toHaveBeenCalledOnce()
    expect(cancelSessionMock).not.toHaveBeenCalled()

    request.resolve(input.session)
    await expectRuntimeReadsTwice()
  })

  it('keeps GET state and blocks duplicate or competing commands while Cancel is pending', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    getSessionMatchesMock.mockResolvedValue(
      input.matches.filter((match) => match.status !== 'PLAYING'),
    )
    const request = deferred<SessionResponse>()
    cancelSessionMock.mockReturnValue(request.promise)
    renderControlRoom()

    await screen.findByRole('button', { name: 'Cancel Session' })
    await user.click(screen.getByRole('button', { name: 'Cancel Session' }))
    const confirmButton = screen.getByRole('button', { name: 'Confirm Cancel' })
    fireEvent.click(confirmButton)
    fireEvent.click(confirmButton)

    expect(await screen.findByRole('button', { name: 'Cancelling…' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Complete Session' })).toBeDisabled()
    expect(within(sessionHeader()).getByText('In progress')).toBeVisible()
    expect(cancelSessionMock).toHaveBeenCalledOnce()
    expect(completeSessionMock).not.toHaveBeenCalled()

    request.resolve(input.session)
    await expectRuntimeReadsTwice()
  })

  it('does not retry Complete after 409 and adopts the refreshed PLAYING Match blocker', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const createdMatches = input.matches.filter(
      (match) => match.status === 'CREATED',
    )
    getSessionMatchesMock
      .mockResolvedValueOnce(createdMatches)
      .mockResolvedValue(input.matches)
    completeSessionMock.mockRejectedValue(new HttpError(409, 'Conflict'))
    renderControlRoom()

    await screen.findByRole('button', { name: 'Complete Session' })
    await confirmComplete(user)

    await expectRuntimeReadsTwice()
    expect(completeSessionMock).toHaveBeenCalledOnce()
    expect(
      screen.getByText('Session state changed. Current runtime data has been refreshed.'),
    ).toBeVisible()
    expect(screen.getByRole('button', { name: 'Complete Session' })).toBeDisabled()
    expect(
      screen.getByText(/unavailable while a Match is PLAYING/i),
    ).toBeVisible()
  })

  it('does not retry Cancel after 409 and keeps feedback scoped to Session controls', async () => {
    const user = userEvent.setup()
    arrangeReadSuccess()
    cancelSessionMock.mockRejectedValue(new HttpError(409, 'Conflict'))
    renderControlRoom()

    await screen.findByRole('button', { name: 'Cancel Session' })
    await confirmCancel(user)

    await expectRuntimeReadsTwice()
    expect(cancelSessionMock).toHaveBeenCalledOnce()
    const controls = screen.getByRole('heading', { name: 'End Session' }).closest('section')
    expect(controls).not.toBeNull()
    expect(
      within(controls as HTMLElement).getByRole('alert'),
    ).toHaveTextContent('Session state changed')
  })

  it('reconciles a network-unknown Complete without replay and renders COMPLETED only from GET', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const createdMatches = input.matches.filter(
      (match) => match.status === 'CREATED',
    )
    const completedSession = sessionWithStatus(input.session, 'COMPLETED')
    getSessionMock
      .mockResolvedValueOnce(input.session)
      .mockResolvedValue(completedSession)
    getSessionMatchesMock.mockResolvedValue(createdMatches)
    completeSessionMock.mockRejectedValue(new TypeError('Network lost'))
    renderControlRoom()

    await screen.findByRole('button', { name: 'Complete Session' })
    await confirmComplete(user)

    await expectRuntimeReadsTwice()
    expect(completeSessionMock).toHaveBeenCalledOnce()
    expect(within(sessionHeader()).getByText('Completed')).toBeVisible()
  })

  it('reconciles a network-unknown Cancel without replay or claiming a definite failure', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    getSessionMatchesMock.mockResolvedValue(
      input.matches.filter((match) => match.status !== 'PLAYING'),
    )
    cancelSessionMock.mockRejectedValue(new TypeError('Network lost'))
    renderControlRoom()

    await screen.findByRole('button', { name: 'Cancel Session' })
    await confirmCancel(user)

    await expectRuntimeReadsTwice()
    expect(cancelSessionMock).toHaveBeenCalledOnce()
    expect(within(sessionHeader()).getByText('In progress')).toBeVisible()
    expect(
      screen.getByText(
        'Connection was lost. Session state has been refreshed; check whether this Session was cancelled.',
      ),
    ).toBeVisible()
  })
})
