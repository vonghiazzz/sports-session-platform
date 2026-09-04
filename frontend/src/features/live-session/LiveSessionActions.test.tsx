import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PropsWithChildren } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  SessionCourtResponse,
  SessionParticipantResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  checkInParticipant,
  disableSessionCourt,
  getPlayers,
  getSession,
  getSessionCourts,
  getSessionMatches,
  getSessionParticipants,
  getVenue,
  getVenueCourts,
  leaveParticipant,
  pauseParticipant,
} from '../../api/liveSessionApi'
import { createLiveSessionInput } from '../../test/liveSessionFixtures'
import { LiveSessionScreen } from './LiveSessionPage'
import { useLiveSessionData } from './useLiveSessionData'

vi.mock('../../api/liveSessionApi', () => ({
  cancelMatch: vi.fn(),
  checkInParticipant: vi.fn(),
  completeMatch: vi.fn(),
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

const checkInParticipantMock = vi.mocked(checkInParticipant)
const disableSessionCourtMock = vi.mocked(disableSessionCourt)
const getPlayersMock = vi.mocked(getPlayers)
const getSessionMock = vi.mocked(getSession)
const getSessionCourtsMock = vi.mocked(getSessionCourts)
const getSessionMatchesMock = vi.mocked(getSessionMatches)
const getSessionParticipantsMock = vi.mocked(getSessionParticipants)
const getVenueMock = vi.mocked(getVenue)
const getVenueCourtsMock = vi.mocked(getVenueCourts)
const leaveParticipantMock = vi.mocked(leaveParticipant)
const pauseParticipantMock = vi.mocked(pauseParticipant)

function deferred<T>() {
  let resolvePromise: (value: T | PromiseLike<T>) => void = () => {
    throw new Error('Deferred promise resolver is unavailable')
  }
  const promise = new Promise<T>((resolve) => {
    resolvePromise = resolve
  })
  return { promise, resolve: resolvePromise }
}

function arrangeReadSuccess() {
  const input = createLiveSessionInput()
  getSessionMock.mockResolvedValue(input.session)
  getSessionParticipantsMock.mockResolvedValue(input.participants)
  getSessionCourtsMock.mockResolvedValue(input.sessionCourts)
  getPlayersMock.mockResolvedValue(input.players)
  getSessionMatchesMock.mockResolvedValue(input.matches)
  getVenueMock.mockResolvedValue(input.venue)
  getVenueCourtsMock.mockResolvedValue(input.venueCourts)
  return input
}

function renderControlRoom() {
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

  function Harness() {
    const state = useLiveSessionData(SESSION_ID)
    return <LiveSessionScreen state={state} now={NOW} />
  }

  return render(<Harness />, { wrapper: Wrapper })
}

function participantRow(groupName: string, playerName: string) {
  const group = screen.getByRole('heading', { name: groupName }).closest('section')
  if (group === null) {
    throw new Error(`Participant group ${groupName} was not rendered`)
  }
  const player = within(group).getByText(playerName)
  const row = player.closest('li')
  if (row === null) {
    throw new Error(`Participant row for ${playerName} was not rendered`)
  }
  return row
}

function courtCard(courtName: string) {
  const board = screen.getByRole('heading', { name: 'Court Board' }).closest('section')
  if (board === null) {
    throw new Error('Court Board was not rendered')
  }
  const heading = within(board).getByRole('heading', { name: courtName })
  const card = heading.closest('article')
  if (card === null) {
    throw new Error(`Court card for ${courtName} was not rendered`)
  }
  return card
}

function participantWithStatus(
  participant: SessionParticipantResponse,
  status: SessionParticipantResponse['status'],
): SessionParticipantResponse {
  return {
    ...participant,
    status,
    checkedInAt:
      status === 'REGISTERED' ? null : '2026-09-02T10:00:00Z',
    waitingSince: status === 'WAITING' ? '2026-09-02T10:00:00Z' : null,
    pausedAt: status === 'PAUSED' ? '2026-09-02T10:00:00Z' : null,
    leftAt: status === 'LEFT' ? '2026-09-02T10:00:00Z' : null,
  }
}

function sessionCourtWithStatus(
  court: SessionCourtResponse,
  status: SessionCourtResponse['status'],
): SessionCourtResponse {
  return { ...court, status }
}

beforeEach(() => {
  vi.resetAllMocks()
})

afterEach(() => {
  queryClients.forEach((queryClient) => queryClient.clear())
  queryClients.length = 0
})

describe('Participant live actions', () => {
  it('re-reads Participants after Check-In and renders the GET response', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const registered = input.participants.find(
      (participant) => participant.status === 'REGISTERED',
    )
    if (registered === undefined) {
      throw new Error('Expected a REGISTERED Participant fixture')
    }
    const waiting = participantWithStatus(registered, 'WAITING')
    getSessionParticipantsMock
      .mockResolvedValueOnce(input.participants)
      .mockResolvedValue(
        input.participants.map((participant) =>
          participant.id === registered.id ? waiting : participant,
        ),
      )
    checkInParticipantMock.mockResolvedValue(waiting)

    renderControlRoom()
    const button = await screen.findByRole('button', { name: 'Check In' })
    await user.click(button)

    expect(checkInParticipantMock).toHaveBeenCalledOnce()
    expect(checkInParticipantMock).toHaveBeenCalledWith(SESSION_ID, registered.id)
    await waitFor(() =>
      expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2),
    )
    expect(getSessionCourtsMock).toHaveBeenCalledTimes(1)
    await waitFor(() =>
      expect(
        within(participantRow('Waiting', 'Chi Le')).getByRole('button', {
          name: 'Pause',
        }),
      ).toBeEnabled(),
    )
  })

  it('keeps WAITING state while Pause is pending and prevents duplicate submission', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const waiting = input.participants.find(
      (participant) => participant.status === 'WAITING',
    )
    if (waiting === undefined) {
      throw new Error('Expected a WAITING Participant fixture')
    }
    const pauseRequest = deferred<SessionParticipantResponse>()
    pauseParticipantMock.mockReturnValue(pauseRequest.promise)

    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    const row = participantRow('Waiting', 'An Nguyen')
    const pauseButton = within(row).getByRole('button', { name: 'Pause' })
    await user.click(pauseButton)

    const pendingButton = within(row).getByRole('button', { name: 'Pausing…' })
    expect(pendingButton).toBeDisabled()
    expect(within(participantRow('Waiting', 'An Nguyen')).getByText('An Nguyen')).toBeVisible()
    expect(within(screen.getByRole('heading', { name: 'Paused' }).closest('section') as HTMLElement).queryByText('An Nguyen')).not.toBeInTheDocument()

    await user.click(pendingButton)
    expect(pauseParticipantMock).toHaveBeenCalledOnce()

    pauseRequest.resolve(participantWithStatus(waiting, 'PAUSED'))
    await waitFor(() =>
      expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2),
    )
    expect(within(participantRow('Waiting', 'An Nguyen')).getByText('An Nguyen')).toBeVisible()
  })

  it('reconciles Participants and shows scoped feedback after a 409', async () => {
    const user = userEvent.setup()
    arrangeReadSuccess()
    pauseParticipantMock.mockRejectedValue(
      new HttpError(409, 'Participant state changed'),
    )

    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    const row = participantRow('Waiting', 'An Nguyen')
    await user.click(within(row).getByRole('button', { name: 'Pause' }))

    await waitFor(() =>
      expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2),
    )
    expect(pauseParticipantMock).toHaveBeenCalledOnce()
    expect(
      within(participantRow('Waiting', 'An Nguyen')).getByRole('alert'),
    ).toHaveTextContent('Live state changed. Current data is being refreshed.')
  })

  it('does not retry an unknown-outcome POST and reconciles Participants', async () => {
    const user = userEvent.setup()
    arrangeReadSuccess()
    leaveParticipantMock.mockRejectedValue(new TypeError('Failed to fetch'))

    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    const row = participantRow('Waiting', 'An Nguyen')
    await user.click(within(row).getByRole('button', { name: 'Leave' }))

    await waitFor(() =>
      expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2),
    )
    expect(leaveParticipantMock).toHaveBeenCalledOnce()
    expect(
      within(participantRow('Waiting', 'An Nguyen')).getByRole('alert'),
    ).toHaveTextContent(
      'Connection was lost. Refresh current state before trying again.',
    )
  })
})

describe('Court live actions', () => {
  it('re-reads Session Courts after Disable and renders the GET response', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const available = input.sessionCourts.find(
      (court) => court.status === 'AVAILABLE',
    )
    if (available === undefined) {
      throw new Error('Expected an AVAILABLE Session Court fixture')
    }
    const unavailable = sessionCourtWithStatus(available, 'UNAVAILABLE')
    getSessionCourtsMock
      .mockResolvedValueOnce(input.sessionCourts)
      .mockResolvedValue(
        input.sessionCourts.map((court) =>
          court.id === available.id ? unavailable : court,
        ),
      )
    disableSessionCourtMock.mockResolvedValue(unavailable)

    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await user.click(
      within(courtCard('Court Two')).getByRole('button', {
        name: 'Disable Court',
      }),
    )

    expect(disableSessionCourtMock).toHaveBeenCalledOnce()
    expect(disableSessionCourtMock).toHaveBeenCalledWith(SESSION_ID, available.id)
    await waitFor(() => expect(getSessionCourtsMock).toHaveBeenCalledTimes(2))
    expect(getSessionParticipantsMock).toHaveBeenCalledTimes(1)
    await waitFor(() =>
      expect(
        within(courtCard('Court Two')).getByRole('button', {
          name: 'Enable Court',
        }),
      ).toBeEnabled(),
    )
  })

  it('keeps AVAILABLE state while Disable is pending and prevents duplicate submission', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const available = input.sessionCourts.find(
      (court) => court.status === 'AVAILABLE',
    )
    if (available === undefined) {
      throw new Error('Expected an AVAILABLE Session Court fixture')
    }
    const disableRequest = deferred<SessionCourtResponse>()
    disableSessionCourtMock.mockReturnValue(disableRequest.promise)

    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    const card = courtCard('Court Two')
    await user.click(
      within(card).getByRole('button', { name: 'Disable Court' }),
    )

    const pendingButton = within(card).getByRole('button', {
      name: 'Disabling…',
    })
    expect(pendingButton).toBeDisabled()
    expect(within(card).getByText('AVAILABLE')).toBeVisible()

    await user.click(pendingButton)
    expect(disableSessionCourtMock).toHaveBeenCalledOnce()

    disableRequest.resolve(sessionCourtWithStatus(available, 'UNAVAILABLE'))
    await waitFor(() => expect(getSessionCourtsMock).toHaveBeenCalledTimes(2))
    expect(within(courtCard('Court Two')).getByText('AVAILABLE')).toBeVisible()
  })

  it('reconciles Session Courts and shows scoped feedback after a 409', async () => {
    const user = userEvent.setup()
    arrangeReadSuccess()
    disableSessionCourtMock.mockRejectedValue(
      new HttpError(409, 'Session Court state changed'),
    )

    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    const card = courtCard('Court Two')
    await user.click(
      within(card).getByRole('button', { name: 'Disable Court' }),
    )

    await waitFor(() => expect(getSessionCourtsMock).toHaveBeenCalledTimes(2))
    expect(disableSessionCourtMock).toHaveBeenCalledOnce()
    expect(within(courtCard('Court Two')).getByRole('alert')).toHaveTextContent(
      'Live state changed. Current data is being refreshed.',
    )
  })
})
