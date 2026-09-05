import type {
  CourtResponse,
  MatchResponse,
  MatchSource,
  ParticipantStatus,
  PlayerResponse,
  SessionCourtResponse,
  SessionCourtStatus,
  SessionParticipantResponse,
  SessionResponse,
  SkillLevel,
  TeamSide,
  VenueResponse,
} from '../../api/contracts'
import {
  formatVietnamDateTime,
  matchSourceLabel,
  skillLevelLabel,
} from '../../lib/presentation'

export interface ParticipantView {
  readonly sessionParticipantId: string
  readonly displayName: string
  readonly status: ParticipantStatus
  readonly skillLevel: SkillLevel | null
  readonly skillLabel: string | null
  readonly waitingSince: string | null
  readonly waitingDuration: string | null
  readonly dataUnavailable: boolean
}

export interface TeamMemberView {
  readonly teamSide: TeamSide
  readonly teamSlot: number
  readonly slotLabel: string
  readonly displayName: string
  readonly dataUnavailable: boolean
}

export interface MatchView {
  readonly id: string
  readonly sessionCourtId: string
  readonly status: 'CREATED' | 'PLAYING'
  readonly source: MatchSource
  readonly sourceLabel: string
  readonly courtName: string
  readonly teamA: readonly TeamMemberView[]
  readonly teamB: readonly TeamMemberView[]
  readonly createdAtLabel: string
  readonly startedAtLabel: string | null
  readonly elapsedLabel: string | null
}

export interface CourtView {
  readonly sessionCourtId: string
  readonly name: string
  readonly status: SessionCourtStatus
  readonly activeMatch: MatchView | null
  readonly dataUnavailable: boolean
}

export interface LiveSessionModel {
  readonly header: {
    readonly title: string
    readonly venueName: string
    readonly venueLocation: string | null
    readonly status: SessionResponse['status']
    readonly sport: SessionResponse['sport']
    readonly matchFormat: SessionResponse['matchFormat']
    readonly plannedStartAtLabel: string
    readonly plannedEndAtLabel: string
    readonly startedAtLabel: string | null
  }
  readonly courts: readonly CourtView[]
  readonly waitingParticipants: readonly ParticipantView[]
  readonly registeredParticipants: readonly ParticipantView[]
  readonly pausedParticipants: readonly ParticipantView[]
  readonly playingParticipants: readonly ParticipantView[]
  readonly leftParticipantCount: number
  readonly createdMatches: readonly MatchView[]
  readonly resolvedMatchCount: number
  readonly warnings: readonly string[]
}

export interface LiveSessionModelInput {
  readonly session: SessionResponse
  readonly venue: VenueResponse
  readonly sessionCourts: readonly SessionCourtResponse[]
  readonly venueCourts: readonly CourtResponse[]
  readonly participants: readonly SessionParticipantResponse[]
  readonly players: readonly PlayerResponse[]
  readonly matches: readonly MatchResponse[]
  readonly now: Date
}

function durationBetween(startValue: string, now: Date): string | null {
  const start = new Date(startValue)
  if (Number.isNaN(start.getTime())) {
    return null
  }

  const totalMinutes = Math.max(
    0,
    Math.floor((now.getTime() - start.getTime()) / 60_000),
  )
  if (totalMinutes < 1) {
    return '<1 phút'
  }
  if (totalMinutes < 60) {
    return `${totalMinutes} phút`
  }

  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  return minutes === 0
    ? `${hours} giờ`
    : `${hours} giờ ${minutes} phút`
}

export function formatWaitingDuration(
  waitingSince: string | null,
  now: Date,
): string | null {
  return waitingSince === null ? null : durationBetween(waitingSince, now)
}

function resolveTeamMember(
  match: MatchResponse,
  side: TeamSide,
  slot: number,
  participantById: ReadonlyMap<string, ParticipantView>,
  warnings: Set<string>,
): TeamMemberView {
  const assignment = match.participants.find(
    (participant) =>
      participant.teamSide === side && participant.teamSlot === slot,
  )

  if (!assignment) {
    warnings.add('Phân công đội của một trận đấu chưa đầy đủ.')
    return {
      teamSide: side,
      teamSlot: slot,
      slotLabel: `${side}${slot}`,
      displayName: 'Không có dữ liệu người chơi',
      dataUnavailable: true,
    }
  }

  const participant = participantById.get(assignment.sessionParticipantId)
  if (!participant) {
    warnings.add('Không thể xác định một người chơi trong trận đấu.')
    return {
      teamSide: side,
      teamSlot: slot,
      slotLabel: `${side}${slot}`,
      displayName: 'Không có dữ liệu người chơi',
      dataUnavailable: true,
    }
  }

  return {
    teamSide: side,
    teamSlot: slot,
    slotLabel: `${side}${slot}`,
    displayName: participant.displayName,
    dataUnavailable: participant.dataUnavailable,
  }
}

export function composeLiveSessionModel({
  session,
  venue,
  sessionCourts,
  venueCourts,
  participants,
  players,
  matches,
  now,
}: LiveSessionModelInput): LiveSessionModel {
  const warnings = new Set<string>()
  const playerById = new Map(players.map((player) => [player.id, player]))

  const participantViews = participants.map<ParticipantView>((participant) => {
    const player = playerById.get(participant.playerId)
    if (!player) {
      warnings.add('Không thể xác định dữ liệu của một số người chơi.')
    }

    const profile = player?.sportProfiles.find(
      (candidate) => candidate.sport === session.sport,
    )
    const waitingDuration =
      participant.status === 'WAITING'
        ? formatWaitingDuration(participant.waitingSince, now)
        : null

    if (participant.status === 'WAITING' && waitingDuration === null) {
      warnings.add('Một người chơi đang chờ không có thời gian chờ hợp lệ.')
    }

    return {
      sessionParticipantId: participant.id,
      displayName: player?.displayName ?? 'Không có dữ liệu người chơi',
      status: participant.status,
      skillLevel: profile?.skillLevel ?? null,
      skillLabel: profile ? skillLevelLabel(profile.skillLevel) : null,
      waitingSince: participant.waitingSince,
      waitingDuration,
      dataUnavailable: player === undefined,
    }
  })

  const participantById = new Map(
    participantViews.map((participant) => [
      participant.sessionParticipantId,
      participant,
    ]),
  )
  const courtById = new Map(venueCourts.map((court) => [court.id, court]))

  const unresolvedCourtViews = sessionCourts.map((sessionCourt) => {
    const court = courtById.get(sessionCourt.courtId)
    if (!court) {
      warnings.add('Không thể xác định dữ liệu của một số sân.')
    }
    return {
      sessionCourtId: sessionCourt.id,
      name: court?.name ?? 'Không có dữ liệu sân',
      status: sessionCourt.status,
      dataUnavailable: court === undefined,
    }
  })
  const courtViewById = new Map(
    unresolvedCourtViews.map((court) => [court.sessionCourtId, court]),
  )

  const liveMatches = matches
    .filter(
      (match): match is MatchResponse & { status: 'CREATED' | 'PLAYING' } =>
        match.status === 'CREATED' || match.status === 'PLAYING',
    )
    .map<MatchView>((match) => {
      const court = courtViewById.get(match.sessionCourtId)
      if (!court) {
        warnings.add('Không thể xác định sân của một trận đấu.')
      }
      const startedAtLabel =
        match.startedAt === null
          ? null
          : formatVietnamDateTime(match.startedAt)

      return {
        id: match.id,
        sessionCourtId: match.sessionCourtId,
        status: match.status,
        source: match.source,
        sourceLabel: matchSourceLabel(match.source),
        courtName: court?.name ?? 'Không có dữ liệu sân',
        teamA: [
          resolveTeamMember(match, 'A', 1, participantById, warnings),
          resolveTeamMember(match, 'A', 2, participantById, warnings),
        ],
        teamB: [
          resolveTeamMember(match, 'B', 1, participantById, warnings),
          resolveTeamMember(match, 'B', 2, participantById, warnings),
        ],
        createdAtLabel: formatVietnamDateTime(match.createdAt),
        startedAtLabel,
        elapsedLabel:
          match.startedAt === null ? null : durationBetween(match.startedAt, now),
      }
    })

  const playingMatches = liveMatches.filter((match) => match.status === 'PLAYING')
  const playingMatchByCourtId = new Map<string, MatchView>()
  for (const match of playingMatches) {
    if (playingMatchByCourtId.has(match.sessionCourtId)) {
      warnings.add('Một sân đang có nhiều hơn một trận đấu diễn ra.')
    } else {
      playingMatchByCourtId.set(match.sessionCourtId, match)
    }
  }

  const courts = unresolvedCourtViews.map<CourtView>((court) => {
    const activeMatch = playingMatchByCourtId.get(court.sessionCourtId) ?? null
    if (court.status === 'PLAYING' && activeMatch === null) {
      warnings.add('Một sân đang chơi không có trận đấu tương ứng.')
    }
    if (court.status !== 'PLAYING' && activeMatch !== null) {
      warnings.add('Một trận đang chơi lại gắn với sân không ở trạng thái đang chơi.')
    }
    return {
      ...court,
      activeMatch,
    }
  })

  const participantsWithStatus = (status: ParticipantStatus) =>
    participantViews.filter((participant) => participant.status === status)

  return {
    header: {
      title: session.title,
      venueName: venue.name,
      venueLocation: venue.locationText,
      status: session.status,
      sport: session.sport,
      matchFormat: session.matchFormat,
      plannedStartAtLabel: formatVietnamDateTime(session.plannedStartAt),
      plannedEndAtLabel: formatVietnamDateTime(session.plannedEndAt),
      startedAtLabel:
        session.startedAt === null
          ? null
          : formatVietnamDateTime(session.startedAt),
    },
    courts,
    waitingParticipants: participantsWithStatus('WAITING').toSorted(
      (left, right) =>
        (left.waitingSince ?? '').localeCompare(right.waitingSince ?? ''),
    ),
    registeredParticipants: participantsWithStatus('REGISTERED'),
    pausedParticipants: participantsWithStatus('PAUSED'),
    playingParticipants: participantsWithStatus('PLAYING'),
    leftParticipantCount: participantsWithStatus('LEFT').length,
    createdMatches: liveMatches.filter((match) => match.status === 'CREATED'),
    resolvedMatchCount: matches.filter(
      (match) => match.status === 'COMPLETED' || match.status === 'CANCELLED',
    ).length,
    warnings: [...warnings],
  }
}
