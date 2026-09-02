import type {
  CourtResponse,
  MatchParticipantResponse,
  MatchResponse,
  ParticipantStatus,
  PlayerResponse,
  SessionCourtResponse,
  SessionParticipantResponse,
  SkillLevel,
} from '../api/contracts'
import type { LiveSessionModelInput } from '../features/live-session/liveSessionModel'

const createdAt = '2026-09-02T08:00:00Z'
const updatedAt = '2026-09-02T09:00:00Z'

function player(id: string, displayName: string, skillLevel: SkillLevel): PlayerResponse {
  return {
    id,
    displayName,
    sportProfiles: [
      {
        id: `profile-${id}`,
        sport: 'BADMINTON',
        skillLevel,
        createdAt,
        updatedAt,
      },
    ],
    createdAt,
    updatedAt,
  }
}

function participant(
  id: string,
  playerId: string,
  status: ParticipantStatus,
  waitingSince: string | null = null,
): SessionParticipantResponse {
  return {
    id,
    sessionId: 'session-1',
    playerId,
    status,
    joinedAt: createdAt,
    checkedInAt: status === 'REGISTERED' ? null : createdAt,
    waitingSince,
    pausedAt: status === 'PAUSED' ? updatedAt : null,
    totalPausedSeconds: 0,
    leftAt: status === 'LEFT' ? updatedAt : null,
    version: 0,
    createdAt,
    updatedAt,
  }
}

function court(
  id: string,
  name: string,
  active = true,
): CourtResponse {
  return {
    id,
    venueId: 'venue-1',
    name,
    sport: 'BADMINTON',
    active,
    createdAt,
    updatedAt,
  }
}

function sessionCourt(
  id: string,
  courtId: string,
  status: SessionCourtResponse['status'],
): SessionCourtResponse {
  return {
    id,
    sessionId: 'session-1',
    courtId,
    status,
    addedAt: createdAt,
    version: 0,
    createdAt,
    updatedAt,
  }
}

function match(
  id: string,
  sessionCourtId: string,
  status: MatchResponse['status'],
  participants: readonly MatchParticipantResponse[],
): MatchResponse {
  return {
    id,
    sessionId: 'session-1',
    sessionCourtId,
    status,
    source: 'MANUAL',
    winnerTeam: null,
    teamAScore: null,
    teamBScore: null,
    resultVersion: 0,
    participants,
    createdAt: '2026-09-02T09:40:00Z',
    startedAt: status === 'PLAYING' ? '2026-09-02T09:45:00Z' : null,
    completedAt: null,
    cancelledAt: null,
    updatedAt,
    version: 0,
  }
}

const playingAssignments: readonly MatchParticipantResponse[] = [
  { sessionParticipantId: 'participant-8', teamSide: 'B', teamSlot: 2 },
  { sessionParticipantId: 'participant-6', teamSide: 'A', teamSlot: 2 },
  { sessionParticipantId: 'participant-7', teamSide: 'B', teamSlot: 1 },
  { sessionParticipantId: 'participant-5', teamSide: 'A', teamSlot: 1 },
]

const createdAssignments: readonly MatchParticipantResponse[] = [
  { sessionParticipantId: 'participant-1', teamSide: 'A', teamSlot: 1 },
  { sessionParticipantId: 'participant-2', teamSide: 'A', teamSlot: 2 },
  { sessionParticipantId: 'participant-3', teamSide: 'B', teamSlot: 1 },
  { sessionParticipantId: 'participant-4', teamSide: 'B', teamSlot: 2 },
]

export function createLiveSessionInput(): LiveSessionModelInput {
  return {
    session: {
      id: 'session-1',
      venueId: 'venue-1',
      title: 'Wednesday Badminton',
      sport: 'BADMINTON',
      matchFormat: 'DOUBLES',
      plannedStartAt: '2026-09-02T09:00:00Z',
      plannedEndAt: '2026-09-02T12:00:00Z',
      status: 'IN_PROGRESS',
      startedAt: '2026-09-02T09:05:00Z',
      completedAt: null,
      cancelledAt: null,
      version: 1,
      createdAt,
      updatedAt,
    },
    venue: {
      id: 'venue-1',
      name: 'Riverside Sports Hall',
      locationText: 'District 2',
      active: true,
      createdAt,
      updatedAt,
    },
    sessionCourts: [
      sessionCourt('session-court-1', 'court-1', 'PLAYING'),
      sessionCourt('session-court-2', 'court-2', 'AVAILABLE'),
      sessionCourt('session-court-3', 'court-3', 'UNAVAILABLE'),
    ],
    venueCourts: [
      court('court-1', 'Court One'),
      court('court-2', 'Court Two'),
      court('court-3', 'Court Three'),
    ],
    participants: [
      participant('participant-1', 'player-1', 'WAITING', '2026-09-02T09:30:00Z'),
      participant('participant-2', 'player-2', 'WAITING', '2026-09-02T09:50:00Z'),
      participant('participant-3', 'player-3', 'REGISTERED'),
      participant('participant-4', 'player-4', 'PAUSED'),
      participant('participant-5', 'player-5', 'PLAYING'),
      participant('participant-6', 'player-6', 'PLAYING'),
      participant('participant-7', 'player-7', 'PLAYING'),
      participant('participant-8', 'player-8', 'PLAYING'),
    ],
    players: [
      player('player-1', 'An Nguyen', 'WEAK'),
      player('player-2', 'Bao Tran', 'WEAK_PLUS'),
      player('player-3', 'Chi Le', 'INTERMEDIATE_MINUS'),
      player('player-4', 'Dung Pham', 'INTERMEDIATE'),
      player('player-5', 'Giang Vo', 'INTERMEDIATE_PLUS'),
      player('player-6', 'Hanh Bui', 'GOOD'),
      player('player-7', 'Khanh Do', 'INTERMEDIATE'),
      player('player-8', 'Linh Ho', 'GOOD'),
    ],
    matches: [
      match('match-playing', 'session-court-1', 'PLAYING', playingAssignments),
      match('match-created', 'session-court-2', 'CREATED', createdAssignments),
    ],
    now: new Date('2026-09-02T10:00:00Z'),
  }
}
