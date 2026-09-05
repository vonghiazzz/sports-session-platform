import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  CourtResponse,
  PlayerResponse,
  SessionCourtResponse,
  SessionParticipantResponse,
} from '../../api/contracts'
import { LiveAddCourt, LiveAddPlayer } from './LiveSessionAdditions'
import {
  useLiveAddCourt,
  useLiveAddPlayer,
} from './useLiveSessionAdditions'

vi.mock('./useLiveSessionAdditions')

const addExistingPlayer = vi.fn(async () => true)
const createAndAddPlayer = vi.fn(async () => true)
const retryCreatedPlayer = vi.fn(async () => true)
const reconcilePlayer = vi.fn(async () => true)
const addCourt = vi.fn(async () => true)
const reconcileCourt = vi.fn(async () => true)

function player(id: string, displayName: string): PlayerResponse {
  return {
    id,
    displayName,
    sportProfiles: [
      {
        id: `profile-${id}`,
        sport: 'BADMINTON',
        skillLevel: 'INTERMEDIATE_PLUS',
        createdAt: '2026-09-01T00:00:00Z',
        updatedAt: '2026-09-01T00:00:00Z',
      },
    ],
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  }
}

function participant(playerId: string): SessionParticipantResponse {
  return {
    id: `participant-${playerId}`,
    sessionId: 'session-1',
    playerId,
    status: 'REGISTERED',
    joinedAt: '2026-09-01T00:00:00Z',
    checkedInAt: null,
    waitingSince: null,
    pausedAt: null,
    totalPausedSeconds: 0,
    leftAt: null,
    version: 0,
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  }
}

function court(id: string, active = true): CourtResponse {
  return {
    id,
    venueId: 'venue-1',
    name: `Sân ${id}`,
    sport: 'BADMINTON',
    active,
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  }
}

const players = [player('existing', 'Người đã tham gia'), player('new', 'Nguyễn Mới')]
const courts = [court('allocated'), court('eligible'), court('inactive', false)]
const sessionCourts: readonly SessionCourtResponse[] = [
  {
    id: 'session-court-1',
    sessionId: 'session-1',
    courtId: 'allocated',
    status: 'AVAILABLE',
    addedAt: '2026-09-01T00:00:00Z',
    version: 0,
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  },
]

beforeEach(() => {
  vi.resetAllMocks()
  addExistingPlayer.mockResolvedValue(true)
  createAndAddPlayer.mockResolvedValue(true)
  retryCreatedPlayer.mockResolvedValue(true)
  reconcilePlayer.mockResolvedValue(true)
  addCourt.mockResolvedValue(true)
  reconcileCourt.mockResolvedValue(true)
  vi.mocked(useLiveAddPlayer).mockReturnValue({
    addExistingPlayer,
    createAndAddPlayer,
    retryCreatedPlayer,
    reconcileUnknown: reconcilePlayer,
    isPending: false,
    recoveryPlayer: null,
    hasUnknownAddOutcome: false,
    createOutcomeUnknown: false,
    message: null,
  })
  vi.mocked(useLiveAddCourt).mockReturnValue({
    addCourt,
    reconcileUnknown: reconcileCourt,
    isPending: false,
    hasUnknownOutcome: false,
    message: null,
  })
})

describe('LiveAddPlayer', () => {
  it('is available only while the Session is IN_PROGRESS', () => {
    const { rerender } = render(
      <LiveAddPlayer
        sessionId="session-1"
        sessionStatus="IN_PROGRESS"
        players={players}
        participants={[participant('existing')]}
      />,
    )
    expect(screen.getByRole('button', { name: '+ Thêm người chơi' })).toBeVisible()

    rerender(
      <LiveAddPlayer
        sessionId="session-1"
        sessionStatus="COMPLETED"
        players={players}
        participants={[participant('existing')]}
      />,
    )
    expect(screen.queryByRole('button', { name: '+ Thêm người chơi' })).not.toBeInTheDocument()
  })

  it('searches eligible Players and excludes a Player already in the Session', async () => {
    const user = userEvent.setup()
    render(
      <LiveAddPlayer
        sessionId="session-1"
        sessionStatus="IN_PROGRESS"
        players={players}
        participants={[participant('existing')]}
      />,
    )
    await user.click(screen.getByRole('button', { name: '+ Thêm người chơi' }))
    await user.type(screen.getByLabelText('Tìm người chơi'), 'mới')

    expect(screen.queryByText('Người đã tham gia')).not.toBeInTheDocument()
    expect(screen.getByText('Nguyễn Mới')).toBeVisible()
    expect(screen.getByText('TB+')).toBeVisible()
  })

  it('selects an existing Player and forwards the selected backend identity', async () => {
    const user = userEvent.setup()
    render(
      <LiveAddPlayer
        sessionId="session-1"
        sessionStatus="IN_PROGRESS"
        players={players}
        participants={[participant('existing')]}
      />,
    )
    await user.click(screen.getByRole('button', { name: '+ Thêm người chơi' }))
    await user.click(screen.getByRole('radio', { name: /Nguyễn Mới/ }))
    await user.click(screen.getByRole('button', { name: 'Thêm vào phiên' }))

    expect(addExistingPlayer).toHaveBeenCalledWith(players[1])
  })

  it('creates a Player with BADMINTON and the selected backend Skill enum', async () => {
    const user = userEvent.setup()
    render(
      <LiveAddPlayer
        sessionId="session-1"
        sessionStatus="IN_PROGRESS"
        players={players}
        participants={[participant('existing')]}
      />,
    )
    await user.click(screen.getByRole('button', { name: '+ Thêm người chơi' }))
    await user.type(screen.getByLabelText('Tìm người chơi'), 'Lê An')
    await user.click(screen.getByRole('button', { name: 'Tạo người chơi mới' }))
    await user.selectOptions(screen.getByLabelText('Trình độ'), 'GOOD')
    await user.click(screen.getByRole('button', { name: 'Tạo và thêm vào phiên' }))

    expect(createAndAddPlayer).toHaveBeenCalledWith({
      displayName: 'Lê An',
      sport: 'BADMINTON',
      skillLevel: 'GOOD',
    })
  })
})

describe('LiveAddCourt', () => {
  it('offers only active, compatible, unallocated Courts and adds the selection', async () => {
    const user = userEvent.setup()
    render(
      <LiveAddCourt
        sessionId="session-1"
        sessionStatus="IN_PROGRESS"
        venueId="venue-1"
        venueCourts={courts}
        sessionCourts={sessionCourts}
      />,
    )
    await user.click(screen.getByRole('button', { name: '+ Thêm sân' }))

    expect(screen.queryByText('Sân allocated')).not.toBeInTheDocument()
    expect(screen.queryByText('Sân inactive')).not.toBeInTheDocument()
    await user.click(screen.getByRole('radio', { name: 'Sân eligible' }))
    await user.click(screen.getByRole('button', { name: 'Thêm sân vào phiên' }))

    expect(addCourt).toHaveBeenCalledWith(courts[1])
  })

  it('does not expose live Court allocation for a terminal Session', () => {
    render(
      <LiveAddCourt
        sessionId="session-1"
        sessionStatus="CANCELLED"
        venueId="venue-1"
        venueCourts={courts}
        sessionCourts={sessionCourts}
      />,
    )
    expect(screen.queryByRole('button', { name: '+ Thêm sân' })).not.toBeInTheDocument()
  })
})
