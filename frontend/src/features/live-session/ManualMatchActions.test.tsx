import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PropsWithChildren } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  MatchResponse,
  SessionCourtResponse,
  SessionParticipantResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  createManualMatch,
  getPlayers,
  getSession,
  getSessionCourts,
  getSessionMatches,
  getSessionParticipants,
  getVenue,
  getVenueCourts,
  startMatch,
} from '../../api/liveSessionApi'
import { createLiveSessionInput } from '../../test/liveSessionFixtures'
import { LiveSessionScreen } from './LiveSessionPage'
import { useLiveSessionData } from './useLiveSessionData'

vi.mock('../../api/liveSessionApi', () => ({
  checkInParticipant: vi.fn(),
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

const createManualMatchMock = vi.mocked(createManualMatch)
const getPlayersMock = vi.mocked(getPlayers)
const getSessionMock = vi.mocked(getSession)
const getSessionCourtsMock = vi.mocked(getSessionCourts)
const getSessionMatchesMock = vi.mocked(getSessionMatches)
const getSessionParticipantsMock = vi.mocked(getSessionParticipants)
const getVenueMock = vi.mocked(getVenue)
const getVenueCourtsMock = vi.mocked(getVenueCourts)
const startMatchMock = vi.mocked(startMatch)

function deferred<T>() {
  let resolvePromise: (value: T | PromiseLike<T>) => void = () => {
    throw new Error('Deferred promise resolver is unavailable')
  }
  const promise = new Promise<T>((resolve) => {
    resolvePromise = resolve
  })
  return { promise, resolve: resolvePromise }
}

function waitingParticipant(
  participant: SessionParticipantResponse,
  index: number,
): SessionParticipantResponse {
  return {
    ...participant,
    status: 'WAITING',
    checkedInAt: '2026-09-02T09:00:00Z',
    waitingSince: `2026-09-02T09:${String(20 + index * 5).padStart(2, '0')}:00Z`,
    pausedAt: null,
    leftAt: null,
  }
}

function playingParticipant(
  participant: SessionParticipantResponse,
): SessionParticipantResponse {
  return {
    ...participant,
    status: 'PLAYING',
    waitingSince: null,
    pausedAt: null,
    leftAt: null,
  }
}

function fourWaitingParticipants(
  participants: readonly SessionParticipantResponse[],
): readonly SessionParticipantResponse[] {
  return participants.map((participant, index) =>
    index < 4 ? waitingParticipant(participant, index) : participant,
  )
}

function newCreatedMatch(template: MatchResponse): MatchResponse {
  return {
    ...template,
    id: 'match-new',
    status: 'CREATED',
    source: 'MANUAL',
    sessionCourtId: 'session-court-2',
    startedAt: null,
    participants: [
      { sessionParticipantId: 'participant-1', teamSide: 'A', teamSlot: 1 },
      { sessionParticipantId: 'participant-2', teamSide: 'A', teamSlot: 2 },
      { sessionParticipantId: 'participant-3', teamSide: 'B', teamSlot: 1 },
      { sessionParticipantId: 'participant-4', teamSide: 'B', teamSlot: 2 },
    ],
  }
}

function startedMatch(template: MatchResponse): MatchResponse {
  return {
    ...template,
    status: 'PLAYING',
    startedAt: '2026-09-02T10:01:00Z',
  }
}

function arrangeReadSuccess() {
  const input = createLiveSessionInput()
  const participants = fourWaitingParticipants(input.participants)
  getSessionMock.mockResolvedValue(input.session)
  getSessionParticipantsMock.mockResolvedValue(participants)
  getSessionCourtsMock.mockResolvedValue(input.sessionCourts)
  getPlayersMock.mockResolvedValue(input.players)
  getSessionMatchesMock.mockResolvedValue(input.matches)
  getVenueMock.mockResolvedValue(input.venue)
  getVenueCourtsMock.mockResolvedValue(input.venueCourts)
  return { ...input, participants }
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

async function selectValidMatch(user: ReturnType<typeof userEvent.setup>) {
  await user.selectOptions(
    screen.getByLabelText('Session Court'),
    'session-court-2',
  )
  await user.selectOptions(
    screen.getByLabelText('Team A — Slot 1'),
    'participant-1',
  )
  await user.selectOptions(
    screen.getByLabelText('Team A — Slot 2'),
    'participant-2',
  )
  await user.selectOptions(
    screen.getByLabelText('Team B — Slot 1'),
    'participant-3',
  )
  await user.selectOptions(
    screen.getByLabelText('Team B — Slot 2'),
    'participant-4',
  )
}

function createdMatchCard() {
  const heading = screen.getAllByRole('heading', { name: 'Court Two' }).at(-1)
  const card = heading?.closest('article')
  if (card === null || card === undefined) {
    throw new Error('Expected a CREATED Match card')
  }
  return card
}

beforeEach(() => {
  vi.resetAllMocks()
})

afterEach(() => {
  queryClients.forEach((queryClient) => queryClient.clear())
  queryClients.length = 0
})

describe('Create Manual Match form', () => {
  it('offers only eligible resources, fixed unique slots, and exact composition', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    createManualMatchMock.mockResolvedValue(newCreatedMatch(input.matches[1]))
    renderControlRoom()

    const courtSelect = await screen.findByLabelText('Session Court')
    expect(within(courtSelect).getByRole('option', { name: 'Court Two' })).toBeEnabled()
    expect(within(courtSelect).queryByRole('option', { name: 'Court One' })).not.toBeInTheDocument()
    expect(within(courtSelect).queryByRole('option', { name: 'Court Three' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Team A — Slot 1')).toBeVisible()
    expect(screen.getByLabelText('Team A — Slot 2')).toBeVisible()
    expect(screen.getByLabelText('Team B — Slot 1')).toBeVisible()
    expect(screen.getByLabelText('Team B — Slot 2')).toBeVisible()
    expect(
      within(screen.getByLabelText('Team A — Slot 1')).queryByRole('option', {
        name: /Giang Vo/,
      }),
    ).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create Match' })).toBeDisabled()

    await user.selectOptions(screen.getByLabelText('Team A — Slot 1'), 'participant-1')
    expect(
      within(screen.getByLabelText('Team A — Slot 2')).getByRole('option', {
        name: /An Nguyen/,
      }),
    ).toBeDisabled()

    await selectValidMatch(user)
    await user.click(screen.getByRole('button', { name: 'Create Match' }))

    expect(createManualMatchMock).toHaveBeenCalledOnce()
    expect(createManualMatchMock).toHaveBeenCalledWith(SESSION_ID, {
      sessionCourtId: 'session-court-2',
      participants: [
        { sessionParticipantId: 'participant-1', teamSide: 'A', teamSlot: 1 },
        { sessionParticipantId: 'participant-2', teamSide: 'A', teamSlot: 2 },
        { sessionParticipantId: 'participant-3', teamSide: 'B', teamSlot: 1 },
        { sessionParticipantId: 'participant-4', teamSide: 'B', teamSlot: 2 },
      ],
    })
    expect(screen.queryByText('participant-1')).not.toBeInTheDocument()
    expect(screen.queryByText(/rating/i)).not.toBeInTheDocument()
  })

  it('shows concise resource explanations without manufacturing options', async () => {
    arrangeReadSuccess()
    getSessionCourtsMock.mockResolvedValue(
      createLiveSessionInput().sessionCourts.map((court) => ({
        ...court,
        status: 'UNAVAILABLE',
      })),
    )
    getSessionParticipantsMock.mockResolvedValue(
      createLiveSessionInput().participants.slice(0, 3),
    )
    renderControlRoom()

    expect(
      await screen.findByText('No AVAILABLE Court can be selected.'),
    ).toBeVisible()
    expect(
      screen.getByText('At least four WAITING players are needed to create a Match.'),
    ).toBeVisible()
    expect(screen.getByRole('button', { name: 'Create Match' })).toBeDisabled()
  })
})

describe('Create Manual Match mutation', () => {
  it('re-reads only Matches on success and renders the refreshed GET Match', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const created = newCreatedMatch(input.matches[1])
    createManualMatchMock.mockResolvedValue(created)
    getSessionMatchesMock
      .mockResolvedValueOnce(input.matches)
      .mockResolvedValue([...input.matches, created])

    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await selectValidMatch(user)
    await user.click(screen.getByRole('button', { name: 'Create Match' }))

    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
    expect(getSessionParticipantsMock).toHaveBeenCalledTimes(1)
    expect(getSessionCourtsMock).toHaveBeenCalledTimes(1)
    expect(getSessionMock).toHaveBeenCalledTimes(1)
    expect(screen.getAllByText('Created — not started')).toHaveLength(2)
    const courtBoard = screen.getByRole('heading', { name: 'Court Board' }).closest('section')
    expect(courtBoard).not.toBeNull()
    const courtTwo = within(courtBoard as HTMLElement)
      .getByRole('heading', { name: 'Court Two' })
      .closest('article')
    expect(courtTwo).not.toBeNull()
    expect(within(courtTwo as HTMLElement).getByText('AVAILABLE')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Waiting' }).closest('section')).toHaveTextContent('An Nguyen')
    expect(
      screen.getByText(/does not reserve its Court or players/i),
    ).toBeVisible()
  })

  it('keeps GET business state while pending and blocks a duplicate submit', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const request = deferred<MatchResponse>()
    createManualMatchMock.mockReturnValue(request.promise)
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await selectValidMatch(user)

    const createButton = screen.getByRole('button', { name: 'Create Match' })
    await user.click(createButton)
    const pendingButton = screen.getByRole('button', { name: 'Creating…' })
    expect(pendingButton).toBeDisabled()
    expect(screen.getAllByText('Created — not started')).toHaveLength(1)
    expect(screen.getByText('AVAILABLE')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Waiting' }).closest('section')).toHaveTextContent('An Nguyen')

    await user.click(pendingButton)
    expect(createManualMatchMock).toHaveBeenCalledOnce()

    request.resolve(newCreatedMatch(input.matches[1]))
    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
  })

  it('does not display a Match returned only by POST response', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    createManualMatchMock.mockResolvedValue(newCreatedMatch(input.matches[1]))
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await selectValidMatch(user)
    await user.click(screen.getByRole('button', { name: 'Create Match' }))

    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
    expect(screen.getAllByText('Created — not started')).toHaveLength(1)
  })

  it('reconciles four runtime reads and shows scoped feedback after 409', async () => {
    const user = userEvent.setup()
    arrangeReadSuccess()
    createManualMatchMock.mockRejectedValue(new HttpError(409, 'Conflict'))
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await selectValidMatch(user)
    await user.click(screen.getByRole('button', { name: 'Create Match' }))

    await waitFor(() => expect(getSessionMock).toHaveBeenCalledTimes(2))
    expect(createManualMatchMock).toHaveBeenCalledOnce()
    expect(getSessionMatchesMock).toHaveBeenCalledTimes(2)
    expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2)
    expect(getSessionCourtsMock).toHaveBeenCalledTimes(2)
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Live resources changed. Current Session state has been refreshed.',
    )
    expect(screen.getAllByText('Created — not started')).toHaveLength(1)
  })

  it('does not retry an unknown Create outcome and warns the Host to inspect Matches', async () => {
    const user = userEvent.setup()
    arrangeReadSuccess()
    createManualMatchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await selectValidMatch(user)
    await user.click(screen.getByRole('button', { name: 'Create Match' }))

    await waitFor(() => expect(getSessionMock).toHaveBeenCalledTimes(2))
    expect(createManualMatchMock).toHaveBeenCalledOnce()
    expect(getSessionMatchesMock).toHaveBeenCalledTimes(2)
    expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2)
    expect(getSessionCourtsMock).toHaveBeenCalledTimes(2)
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Check Created Matches before creating again.',
    )
  })
})

describe('Start Match mutation', () => {
  it('re-reads Matches, Participants, and Courts and renders their PLAYING state', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const created = input.matches[1]
    const playing = startedMatch(created)
    const playingParticipants = input.participants.map((participant, index) =>
      index < 4 ? playingParticipant(participant) : participant,
    )
    const playingCourts = input.sessionCourts.map<SessionCourtResponse>((court) =>
      court.id === created.sessionCourtId ? { ...court, status: 'PLAYING' } : court,
    )
    startMatchMock.mockResolvedValue(playing)
    getSessionMatchesMock
      .mockResolvedValueOnce(input.matches)
      .mockResolvedValue(input.matches.map((match) => match.id === created.id ? playing : match))
    getSessionParticipantsMock
      .mockResolvedValueOnce(input.participants)
      .mockResolvedValue(playingParticipants)
    getSessionCourtsMock
      .mockResolvedValueOnce(input.sessionCourts)
      .mockResolvedValue(playingCourts)

    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await user.click(within(createdMatchCard()).getByRole('button', { name: 'Start Match' }))

    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
    expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2)
    expect(getSessionCourtsMock).toHaveBeenCalledTimes(2)
    expect(getSessionMock).toHaveBeenCalledTimes(1)
    await waitFor(() =>
      expect(screen.queryByText('Created — not started')).not.toBeInTheDocument(),
    )
    const courtTwo = screen.getByRole('heading', { name: 'Court Two' }).closest('article')
    expect(courtTwo).not.toBeNull()
    expect(within(courtTwo as HTMLElement).getByText('PLAYING')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Playing' }).closest('section')).toHaveTextContent('An Nguyen')
  })

  it('keeps CREATED/WAITING/AVAILABLE state while pending and blocks duplicate Start', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const request = deferred<MatchResponse>()
    startMatchMock.mockReturnValue(request.promise)
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    const card = createdMatchCard()
    await user.click(within(card).getByRole('button', { name: 'Start Match' }))

    const pendingButton = within(card).getByRole('button', { name: 'Starting…' })
    expect(pendingButton).toBeDisabled()
    expect(within(card).getByText('Created — not started')).toBeVisible()
    expect(screen.getByText('AVAILABLE')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Waiting' }).closest('section')).toHaveTextContent('An Nguyen')

    await user.click(pendingButton)
    expect(startMatchMock).toHaveBeenCalledOnce()
    request.resolve(startedMatch(input.matches[1]))
    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
  })

  it('does not render PLAYING state returned only by the Start response', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    startMatchMock.mockResolvedValue(startedMatch(input.matches[1]))
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await user.click(
      within(createdMatchCard()).getByRole('button', { name: 'Start Match' }),
    )

    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
    expect(screen.getByText('Created — not started')).toBeVisible()
    expect(screen.getByText('AVAILABLE')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Waiting' }).closest('section')).toHaveTextContent('An Nguyen')
  })

  it('reconciles four runtime reads and shows scoped feedback after 409', async () => {
    const user = userEvent.setup()
    arrangeReadSuccess()
    startMatchMock.mockRejectedValue(new HttpError(409, 'Conflict'))
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await user.click(
      within(createdMatchCard()).getByRole('button', { name: 'Start Match' }),
    )

    await waitFor(() => expect(getSessionMock).toHaveBeenCalledTimes(2))
    expect(startMatchMock).toHaveBeenCalledOnce()
    expect(getSessionMatchesMock).toHaveBeenCalledTimes(2)
    expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2)
    expect(getSessionCourtsMock).toHaveBeenCalledTimes(2)
    expect(within(createdMatchCard()).getByRole('alert')).toHaveTextContent(
      'Live resources changed. Current Session state has been refreshed.',
    )
  })

  it('does not retry an unknown Start outcome and lets refreshed GET state decide', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const created = input.matches[1]
    const playing = startedMatch(created)
    startMatchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    getSessionMatchesMock
      .mockResolvedValueOnce(input.matches)
      .mockResolvedValue(input.matches.map((match) => match.id === created.id ? playing : match))
    getSessionParticipantsMock
      .mockResolvedValueOnce(input.participants)
      .mockResolvedValue(
        input.participants.map((participant, index) =>
          index < 4 ? playingParticipant(participant) : participant,
        ),
      )
    getSessionCourtsMock
      .mockResolvedValueOnce(input.sessionCourts)
      .mockResolvedValue(
        input.sessionCourts.map((court) =>
          court.id === created.sessionCourtId
            ? { ...court, status: 'PLAYING' }
            : court,
        ),
      )

    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await user.click(
      within(createdMatchCard()).getByRole('button', { name: 'Start Match' }),
    )

    await waitFor(() => expect(getSessionMock).toHaveBeenCalledTimes(2))
    expect(startMatchMock).toHaveBeenCalledOnce()
    await waitFor(() =>
      expect(screen.queryByText('Created — not started')).not.toBeInTheDocument(),
    )
    const courtTwo = screen.getByRole('heading', { name: 'Court Two' }).closest('article')
    expect(courtTwo).not.toBeNull()
    expect(within(courtTwo as HTMLElement).getByText('PLAYING')).toBeVisible()
  })
})
