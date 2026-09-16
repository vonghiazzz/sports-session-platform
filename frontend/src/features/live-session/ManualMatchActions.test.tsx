import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PropsWithChildren } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  MatchResponse,
  SessionCourtResponse,
  SessionParticipantResponse,
} from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
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
import { getSessionMatchPlans } from '../../api/matchPlanApi'
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
vi.mock('../../api/matchPlanApi', () => ({
  getSessionMatchPlans: vi.fn(),
}))

const SESSION_ID = 'session-1'
const NOW = new Date('2026-09-02T10:00:00Z')
const queryClients: QueryClient[] = []

const getPlayersMock = vi.mocked(getPlayers)
const getSessionMock = vi.mocked(getSession)
const getSessionCourtsMock = vi.mocked(getSessionCourts)
const getSessionMatchesMock = vi.mocked(getSessionMatches)
const getSessionParticipantsMock = vi.mocked(getSessionParticipants)
const getVenueMock = vi.mocked(getVenue)
const getVenueCourtsMock = vi.mocked(getVenueCourts)
const startMatchMock = vi.mocked(startMatch)
const getSessionMatchPlansMock = vi.mocked(getSessionMatchPlans)

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
  getSessionMatchPlansMock.mockResolvedValue(input.matchPlans)
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
      <MemoryRouter>
        <QueryClientProvider client={queryClient}>
          {children}
        </QueryClientProvider>
      </MemoryRouter>
    )
  }

  function Harness() {
    const state = useLiveSessionData(SESSION_ID)
    return <LiveSessionScreen state={state} now={NOW} />
  }

  return render(<Harness />, { wrapper: Wrapper })
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
    await user.click(within(createdMatchCard()).getByRole('button', { name: 'Bắt đầu trận' }))

    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
    expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2)
    expect(getSessionCourtsMock).toHaveBeenCalledTimes(2)
    expect(getSessionMock).toHaveBeenCalledTimes(1)
    await waitFor(() =>
      expect(screen.queryByText('Đã tạo — chưa bắt đầu')).not.toBeInTheDocument(),
    )
    const courtTwo = screen.getByRole('heading', { name: 'Court Two' }).closest('article')
    expect(courtTwo).not.toBeNull()
    expect(within(courtTwo as HTMLElement).getByText('Đang chơi')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Đang chơi' }).closest('section')).toHaveTextContent('An Nguyen')
  })

  it('keeps CREATED/WAITING/AVAILABLE state while pending and blocks duplicate Start', async () => {
    const user = userEvent.setup()
    const input = arrangeReadSuccess()
    const request = deferred<MatchResponse>()
    startMatchMock.mockReturnValue(request.promise)
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    const card = createdMatchCard()
    await user.click(within(card).getByRole('button', { name: 'Bắt đầu trận' }))

    const pendingButton = within(card).getByRole('button', { name: 'Đang bắt đầu…' })
    expect(pendingButton).toBeDisabled()
    expect(within(card).getByText('Đã tạo — chưa bắt đầu')).toBeVisible()
    expect(screen.getByText('Sẵn sàng')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Đang chờ' }).closest('section')).toHaveTextContent('An Nguyen')

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
      within(createdMatchCard()).getByRole('button', { name: 'Bắt đầu trận' }),
    )

    await waitFor(() => expect(getSessionMatchesMock).toHaveBeenCalledTimes(2))
    expect(screen.getByText('Đã tạo — chưa bắt đầu')).toBeVisible()
    expect(screen.getByText('Sẵn sàng')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Đang chờ' }).closest('section')).toHaveTextContent('An Nguyen')
  })

  it('reconciles four runtime reads and shows scoped feedback after 409', async () => {
    const user = userEvent.setup()
    arrangeReadSuccess()
    startMatchMock.mockRejectedValue(new HttpError(409, 'Conflict'))
    renderControlRoom()
    await screen.findByRole('heading', { name: 'Wednesday Badminton' })
    await user.click(
      within(createdMatchCard()).getByRole('button', { name: 'Bắt đầu trận' }),
    )

    await waitFor(() => expect(getSessionMock).toHaveBeenCalledTimes(2))
    expect(startMatchMock).toHaveBeenCalledOnce()
    expect(getSessionMatchesMock).toHaveBeenCalledTimes(2)
    expect(getSessionParticipantsMock).toHaveBeenCalledTimes(2)
    expect(getSessionCourtsMock).toHaveBeenCalledTimes(2)
    expect(within(createdMatchCard()).getByRole('alert')).toHaveTextContent(
      'Tài nguyên trực tiếp đã thay đổi. Trạng thái phiên hiện tại đã được tải lại.',
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
      within(createdMatchCard()).getByRole('button', { name: 'Bắt đầu trận' }),
    )

    await waitFor(() => expect(getSessionMock).toHaveBeenCalledTimes(2))
    expect(startMatchMock).toHaveBeenCalledOnce()
    await waitFor(() =>
      expect(screen.queryByText('Đã tạo — chưa bắt đầu')).not.toBeInTheDocument(),
    )
    const courtTwo = screen.getByRole('heading', { name: 'Court Two' }).closest('article')
    expect(courtTwo).not.toBeNull()
    expect(within(courtTwo as HTMLElement).getByText('Đang chơi')).toBeVisible()
  })
})
