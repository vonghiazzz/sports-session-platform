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
  cancelMatch,
  completeMatch,
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

const cancelMatchMock = vi.mocked(cancelMatch)
const completeMatchMock = vi.mocked(completeMatch)
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

function playingCourtCard() {
  const board = screen.getByRole('heading', { name: 'Court Board' }).closest('section')
  const heading = board
    ? within(board).getByRole('heading', { name: 'Court One' })
    : null
  const card = heading?.closest('article')
  if (card === null || card === undefined) {
    throw new Error('Expected the PLAYING Match Court card')
  }
  return card
}

function createdMatchCard() {
  const heading = screen.getAllByRole('heading', { name: 'Court Two' }).at(-1)
  const card = heading?.closest('article')
  if (card === null || card === undefined) {
    throw new Error('Expected the CREATED Match card')
  }
  return card
}

function resolveMatch(
  match: MatchResponse,
  status: 'COMPLETED' | 'CANCELLED',
): MatchResponse {
  return {
    ...match,
    status,
    winnerTeam: status === 'COMPLETED' ? 'A' : null,
    teamAScore: status === 'COMPLETED' ? 21 : null,
    teamBScore: status === 'COMPLETED' ? 17 : null,
    resultVersion: status === 'COMPLETED' ? 1 : 0,
    completedAt:
      status === 'COMPLETED' ? '2026-09-02T10:02:00Z' : null,
    cancelledAt:
      status === 'CANCELLED' ? '2026-09-02T10:02:00Z' : null,
  }
}

function releaseParticipants(
  participants: readonly SessionParticipantResponse[],
  participantIds: ReadonlySet<string>,
): readonly SessionParticipantResponse[] {
  return participants.map((participant) =>
    participantIds.has(participant.id)
      ? {
          ...participant,
          status: 'WAITING',
          waitingSince: '2026-09-02T10:02:00Z',
        }
      : participant,
  )
}

function releaseCourt(
  courts: readonly SessionCourtResponse[],
  sessionCourtId: string,
): readonly SessionCourtResponse[] {
  return courts.map((court) =>
    court.id === sessionCourtId ? { ...court, status: 'AVAILABLE' } : court,
  )
}

function arrangeResolvedReads(
  input: ReturnType<typeof createLiveSessionInput>,
  status: 'COMPLETED' | 'CANCELLED',
) {
  const playing = input.matches.find((match) => match.status === 'PLAYING')
  if (playing === undefined) {
    throw new Error('Expected a PLAYING Match fixture')
  }
  const resolved = resolveMatch(playing, status)
  const participantIds = new Set(
    playing.participants.map((participant) => participant.sessionParticipantId),
  )
  getSessionMatchesMock
    .mockResolvedValueOnce(input.matches)
    .mockResolvedValue(
      input.matches.map((match) => (match.id === playing.id ? resolved : match)),
    )
  getSessionParticipantsMock
    .mockResolvedValueOnce(input.participants)
    .mockResolvedValue(releaseParticipants(input.participants, participantIds))
  getSessionCourtsMock
    .mockResolvedValueOnce(input.sessionCourts)
    .mockResolvedValue(releaseCourt(input.sessionCourts, playing.sessionCourtId))
  return { playing, resolved }
}

async function chooseWinner(
  user: ReturnType<typeof userEvent.setup>,
  winner: 'A' | 'B',
) {
  await user.selectOptions(
    within(playingCourtCard()).getByLabelText('Winner'),
    winner,
  )
}

async function enterScores(
  user: ReturnType<typeof userEvent.setup>,
  teamAScore: string,
  teamBScore: string,
) {
  const card = playingCourtCard()
  if (teamAScore !== '') {
    await user.type(within(card).getByLabelText('Team A Score (optional)'), teamAScore)
  }
  if (teamBScore !== '') {
    await user.type(within(card).getByLabelText('Team B Score (optional)'), teamBScore)
  }
}

async function requestCancel(
  user: ReturnType<typeof userEvent.setup>,
  card: HTMLElement,
) {
  await user.click(within(card).getByRole('button', { name: 'Cancel Match' }))
  await user.click(within(card).getByRole('button', { name: 'Confirm Cancel' }))
}

beforeEach(() => {
  vi.resetAllMocks()
})

afterEach(() => {
  queryClients.forEach((queryClient) => queryClient.clear())
  queryClients.length = 0
})

describe('Complete Match result form', () => {
  it('allows winner-only completion and serializes blank scores as null', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const playing = input.matches.find((match) => match.status === 'PLAYING')
    if (playing === undefined) {
      throw new Error('Expected a PLAYING Match fixture')
    }
    completeMatchMock.mockResolvedValue(resolveMatch(playing, 'COMPLETED'))
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })

    await chooseWinner(user, 'A')
    await user.click(
      within(playingCourtCard()).getByRole('button', { name: 'Complete Match' }),
    )

    expect(completeMatchMock).toHaveBeenCalledOnce()
    expect(completeMatchMock).toHaveBeenCalledWith('match-playing', {
      winnerTeam: 'A',
      teamAScore: null,
      teamBScore: null,
    })
  })

  it.each([
    {
      name: 'missing winner',
      winner: '' as const,
      a: '',
      b: '',
      message: 'Choose the winning team.',
    },
    {
      name: 'one missing score',
      winner: 'A' as const,
      a: '21',
      b: '',
      message: 'Enter both scores or leave both blank.',
    },
    {
      name: 'negative score',
      winner: 'A' as const,
      a: '-1',
      b: '0',
      message: 'Scores cannot be negative.',
    },
    {
      name: 'non-integer score',
      winner: 'A' as const,
      a: '21.5',
      b: '17',
      message: 'Scores must be whole numbers.',
    },
    {
      name: 'tie',
      winner: 'A' as const,
      a: '15',
      b: '15',
      message: 'A Match cannot end in a draw.',
    },
    {
      name: 'Team A winner with lower score',
      winner: 'A' as const,
      a: '15',
      b: '21',
      message: 'The winning team must have the higher score.',
    },
    {
      name: 'Team B winner with lower score',
      winner: 'B' as const,
      a: '21',
      b: '15',
      message: 'The winning team must have the higher score.',
    },
  ])('rejects $name without a POST', async ({ winner, a, b, message }) => {
    const user = userEvent.setup()
    arrangeReadSuccess()
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    if (winner !== '') {
      await chooseWinner(user, winner)
    }
    await enterScores(user, a, b)

    await user.click(
      within(playingCourtCard()).getByRole('button', { name: 'Complete Match' }),
    )

    expect(completeMatchMock).not.toHaveBeenCalled()
    expect(within(playingCourtCard()).getByRole('alert')).toHaveTextContent(message)
  })

  it.each([
    { winner: 'A' as const, a: '21', b: '17' },
    { winner: 'B' as const, a: '12', b: '21' },
    { winner: 'A' as const, a: '1', b: '0' },
  ])('submits exact valid $winner score $a-$b', async ({ winner, a, b }) => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const playing = input.matches.find((match) => match.status === 'PLAYING')
    if (playing === undefined) {
      throw new Error('Expected a PLAYING Match fixture')
    }
    completeMatchMock.mockResolvedValue(resolveMatch(playing, 'COMPLETED'))
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await chooseWinner(user, winner)
    await enterScores(user, a, b)

    await user.click(
      within(playingCourtCard()).getByRole('button', { name: 'Complete Match' }),
    )

    expect(completeMatchMock).toHaveBeenCalledWith('match-playing', {
      winnerTeam: winner,
      teamAScore: Number(a),
      teamBScore: Number(b),
    })
  })
})

describe('Complete Match lifecycle', () => {
  it('renders completed resource release only from authoritative GET responses', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const { resolved } = arrangeResolvedReads(input, 'COMPLETED')
    completeMatchMock.mockResolvedValue(resolved)
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await chooseWinner(user, 'A')
    await enterScores(user, '21', '17')

    await user.click(
      within(playingCourtCard()).getByRole('button', { name: 'Complete Match' }),
    )

    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
    expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2)
    expect(getSessionCourtsMock).toHaveBeenCalledTimes(2)
    expect(getSessionMock).toHaveBeenCalledTimes(1)
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Complete Match' })).not.toBeInTheDocument(),
    )
    expect(within(playingCourtCard()).getByText('AVAILABLE')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Waiting' }).closest('section')).toHaveTextContent('Giang Vo')
  })

  it('keeps PLAYING state pending, blocks duplicate Complete and same-Match Cancel', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const request = deferred<MatchResponse>()
    completeMatchMock.mockReturnValue(request.promise)
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await chooseWinner(user, 'A')
    const card = playingCourtCard()

    await user.click(within(card).getByRole('button', { name: 'Complete Match' }))

    const pendingButton = within(card).getByRole('button', { name: 'Completing…' })
    expect(pendingButton).toBeDisabled()
    expect(within(card).getByText('PLAYING')).toBeVisible()
    expect(within(card).getByRole('button', { name: 'Cancel Match' })).toBeDisabled()
    expect(screen.getByRole('heading', { name: 'Playing' }).closest('section')).toHaveTextContent('Giang Vo')
    await user.click(pendingButton)
    await user.click(within(card).getByRole('button', { name: 'Cancel Match' }))
    expect(completeMatchMock).toHaveBeenCalledOnce()
    expect(cancelMatchMock).not.toHaveBeenCalled()

    request.resolve(resolveMatch(input.matches[0], 'COMPLETED'))
    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
  })

  it('keeps GET PLAYING state when only the Complete response says COMPLETED', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    completeMatchMock.mockResolvedValue(resolveMatch(input.matches[0], 'COMPLETED'))
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await chooseWinner(user, 'A')
    await user.click(
      within(playingCourtCard()).getByRole('button', { name: 'Complete Match' }),
    )

    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
    expect(within(playingCourtCard()).getByText('PLAYING')).toBeVisible()
    expect(within(playingCourtCard()).getByLabelText('Winner')).toHaveValue('A')
    expect(screen.getByRole('heading', { name: 'Playing' }).closest('section')).toHaveTextContent('Giang Vo')
  })

  it('reconciles three reads and retains GET state after a Complete 409', async () => {
    const user = userEvent.setup()
    arrangeReadSuccess()
    completeMatchMock.mockRejectedValue(new HttpError(409, 'Conflict'))
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await chooseWinner(user, 'A')
    await user.click(
      within(playingCourtCard()).getByRole('button', { name: 'Complete Match' }),
    )

    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
    expect(completeMatchMock).toHaveBeenCalledOnce()
    expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2)
    expect(getSessionCourtsMock).toHaveBeenCalledTimes(2)
    expect(getSessionMock).toHaveBeenCalledTimes(1)
    expect(within(playingCourtCard()).getAllByRole('alert').at(-1)).toHaveTextContent(
      'Live resources changed. Current Match state has been refreshed.',
    )
    expect(within(playingCourtCard()).getByText('PLAYING')).toBeVisible()
  })

  it('does not retry an unknown Complete outcome and lets refreshed GETs resolve it', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    arrangeResolvedReads(input, 'COMPLETED')
    completeMatchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await chooseWinner(user, 'A')
    await user.click(
      within(playingCourtCard()).getByRole('button', { name: 'Complete Match' }),
    )

    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
    expect(completeMatchMock).toHaveBeenCalledOnce()
    expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2)
    expect(getSessionCourtsMock).toHaveBeenCalledTimes(2)
    expect(getSessionMock).toHaveBeenCalledTimes(1)
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Complete Match' })).not.toBeInTheDocument(),
    )
    expect(within(playingCourtCard()).getByText('AVAILABLE')).toBeVisible()
  })

  it('shows safe scoped feedback when an unknown Complete outcome remains PLAYING', async () => {
    const user = userEvent.setup()
    arrangeReadSuccess()
    completeMatchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await chooseWinner(user, 'A')

    await user.click(
      within(playingCourtCard()).getByRole('button', { name: 'Complete Match' }),
    )

    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
    expect(completeMatchMock).toHaveBeenCalledOnce()
    expect(within(playingCourtCard()).getByRole('alert')).toHaveTextContent(
      'Connection was lost. Match state has been refreshed; check whether this Match completed.',
    )
    expect(within(playingCourtCard()).getByText('PLAYING')).toBeVisible()
    expect(within(playingCourtCard()).getByLabelText('Winner')).toHaveValue('A')
  })
})

describe('Cancel Match lifecycle', () => {
  it('cancels CREATED and rereads Matches, Participants, and Courts for race safety', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const created = input.matches.find((match) => match.status === 'CREATED')
    if (created === undefined) {
      throw new Error('Expected a CREATED Match fixture')
    }
    const cancelled = resolveMatch(created, 'CANCELLED')
    cancelMatchMock.mockResolvedValue(cancelled)
    getSessionMatchesMock
      .mockResolvedValueOnce(input.matches)
      .mockResolvedValue(
        input.matches.map((match) => (match.id === created.id ? cancelled : match)),
      )
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })

    await requestCancel(user, createdMatchCard())

    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
    expect(cancelMatchMock).toHaveBeenCalledOnce()
    expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2)
    expect(getSessionCourtsMock).toHaveBeenCalledTimes(2)
    expect(getSessionMock).toHaveBeenCalledTimes(1)
    expect(screen.queryByText('Created — not started')).not.toBeInTheDocument()
    expect(screen.getByText('AVAILABLE')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Waiting' }).closest('section')).toHaveTextContent('An Nguyen')
  })

  it('cancels PLAYING and renders resource release from GET data', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const { resolved } = arrangeResolvedReads(input, 'CANCELLED')
    cancelMatchMock.mockResolvedValue(resolved)
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })

    await requestCancel(user, playingCourtCard())

    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
    expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2)
    expect(getSessionCourtsMock).toHaveBeenCalledTimes(2)
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Complete Match' })).not.toBeInTheDocument(),
    )
    expect(within(playingCourtCard()).getByText('AVAILABLE')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Waiting' }).closest('section')).toHaveTextContent('Giang Vo')
  })

  it('keeps PLAYING state when Cancel is pending or only its POST response is CANCELLED', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const request = deferred<MatchResponse>()
    cancelMatchMock.mockReturnValue(request.promise)
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    const card = playingCourtCard()
    await user.click(within(card).getByRole('button', { name: 'Cancel Match' }))
    await user.click(within(card).getByRole('button', { name: 'Confirm Cancel' }))

    const pendingButton = within(card).getByRole('button', { name: 'Cancelling…' })
    expect(pendingButton).toBeDisabled()
    expect(within(card).getByText('PLAYING')).toBeVisible()
    expect(within(card).getByRole('button', { name: 'Complete Match' })).toBeDisabled()
    expect(screen.getByRole('heading', { name: 'Playing' }).closest('section')).toHaveTextContent('Giang Vo')
    await user.click(pendingButton)
    await user.click(within(card).getByRole('button', { name: 'Complete Match' }))
    expect(cancelMatchMock).toHaveBeenCalledOnce()
    expect(completeMatchMock).not.toHaveBeenCalled()

    request.resolve(resolveMatch(input.matches[0], 'CANCELLED'))
    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
    expect(within(playingCourtCard()).getByText('PLAYING')).toBeVisible()
  })

  it('blocks same-Match Cancel while Start is pending', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const request = deferred<MatchResponse>()
    startMatchMock.mockReturnValue(request.promise)
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    const card = createdMatchCard()
    await user.click(within(card).getByRole('button', { name: 'Start Match' }))

    expect(within(card).getByRole('button', { name: 'Starting…' })).toBeDisabled()
    expect(within(card).getByRole('button', { name: 'Cancel Match' })).toBeDisabled()
    await user.click(within(card).getByRole('button', { name: 'Cancel Match' }))
    expect(startMatchMock).toHaveBeenCalledOnce()
    expect(cancelMatchMock).not.toHaveBeenCalled()

    request.resolve(input.matches[1])
    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
  })

  it('reconciles three reads and keeps GET state after a Cancel 409', async () => {
    const user = userEvent.setup()
    arrangeReadSuccess()
    cancelMatchMock.mockRejectedValue(new HttpError(409, 'Conflict'))
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    const card = playingCourtCard()

    await requestCancel(user, card)

    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
    expect(cancelMatchMock).toHaveBeenCalledOnce()
    expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2)
    expect(getSessionCourtsMock).toHaveBeenCalledTimes(2)
    expect(getSessionMock).toHaveBeenCalledTimes(1)
    expect(within(card).getAllByRole('alert').at(-1)).toHaveTextContent(
      'Live resources changed. Current Match state has been refreshed.',
    )
    expect(within(card).getByText('PLAYING')).toBeVisible()
  })

  it('does not retry an unknown Cancel outcome and lets refreshed GETs decide', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    arrangeResolvedReads(input, 'CANCELLED')
    cancelMatchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })

    await requestCancel(user, playingCourtCard())

    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
    expect(cancelMatchMock).toHaveBeenCalledOnce()
    expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2)
    expect(getSessionCourtsMock).toHaveBeenCalledTimes(2)
    expect(getSessionMock).toHaveBeenCalledTimes(1)
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Complete Match' })).not.toBeInTheDocument(),
    )
    expect(within(playingCourtCard()).getByText('AVAILABLE')).toBeVisible()
  })

  it('shows safe scoped feedback when an unknown Cancel outcome remains PLAYING', async () => {
    const user = userEvent.setup()
    arrangeReadSuccess()
    cancelMatchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    const card = playingCourtCard()

    await requestCancel(user, card)

    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
    expect(cancelMatchMock).toHaveBeenCalledOnce()
    expect(within(card).getByRole('alert')).toHaveTextContent(
      'Connection was lost. Match state has been refreshed; check whether this Match was cancelled.',
    )
    expect(within(card).getByText('PLAYING')).toBeVisible()
  })

  it('keeps lifecycle guards target-scoped across different Matches', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const completeRequest = deferred<MatchResponse>()
    const cancelRequest = deferred<MatchResponse>()
    completeMatchMock.mockReturnValue(completeRequest.promise)
    cancelMatchMock.mockReturnValue(cancelRequest.promise)
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await chooseWinner(user, 'A')

    await user.click(
      within(playingCourtCard()).getByRole('button', { name: 'Complete Match' }),
    )
    const createdCard = createdMatchCard()
    expect(
      within(createdCard).getByRole('button', { name: 'Cancel Match' }),
    ).toBeEnabled()
    await requestCancel(user, createdCard)

    expect(completeMatchMock).toHaveBeenCalledOnce()
    expect(cancelMatchMock).toHaveBeenCalledOnce()

    completeRequest.resolve(resolveMatch(input.matches[0], 'COMPLETED'))
    cancelRequest.resolve(resolveMatch(input.matches[1], 'CANCELLED'))
    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(3))
  })
})
