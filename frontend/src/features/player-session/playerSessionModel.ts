import type {
  MatchParticipantResponse,
  MatchPlanParticipantResponse,
  ParticipantStatus,
  SessionParticipantResponse,
} from '../../api/contracts'
import type { LiveSessionData } from '../live-session/useLiveSessionData'

export type PlayerSessionDisplayState = ParticipantStatus | 'QUEUED'

export interface PlayerSessionPersonView {
  readonly sessionParticipantId: string
  readonly participantCode: number
  readonly displayName: string
}

export interface PlayerSessionActivityView {
  readonly courtName: string
  readonly teammate: PlayerSessionPersonView | null
  readonly opponents: readonly PlayerSessionPersonView[]
  readonly detailsIncomplete: boolean
}

export interface PlayerSessionViewModel {
  readonly sessionId: string
  readonly sessionTitle: string
  readonly sessionStatus: LiveSessionData['session']['status']
  readonly participant: PlayerSessionPersonView
  readonly displayState: PlayerSessionDisplayState
  readonly activity: PlayerSessionActivityView | null
  readonly detailsIncomplete: boolean
}

function resolveCourtName(
  data: LiveSessionData,
  sessionCourtId: string,
): string | null {
  const sessionCourt = data.sessionCourts.find(
    (candidate) => candidate.id === sessionCourtId,
  )
  if (sessionCourt === undefined) {
    return null
  }
  return (
    data.venueCourts.find((court) => court.id === sessionCourt.courtId)?.name ??
    null
  )
}

function resolveActivity(
  assignments:
    | readonly MatchParticipantResponse[]
    | readonly MatchPlanParticipantResponse[],
  sessionParticipantId: string,
  courtName: string | null,
  personByParticipantId: ReadonlyMap<string, PlayerSessionPersonView>,
): PlayerSessionActivityView {
  const targetAssignments = assignments.filter(
    (assignment) =>
      assignment.sessionParticipantId === sessionParticipantId,
  )
  if (targetAssignments.length !== 1) {
    return {
      courtName: courtName ?? 'Không có dữ liệu sân',
      teammate: null,
      opponents: [],
      detailsIncomplete: true,
    }
  }

  const targetSide = targetAssignments[0].teamSide
  const teammateAssignments = assignments.filter(
    (assignment) =>
      assignment.teamSide === targetSide &&
      assignment.sessionParticipantId !== sessionParticipantId,
  ).toSorted(
    (left, right) =>
      left.teamSlot - right.teamSlot ||
      left.sessionParticipantId.localeCompare(right.sessionParticipantId),
  )
  const opponentAssignments = assignments
    .filter((assignment) => assignment.teamSide !== targetSide)
    .toSorted(
      (left, right) =>
        left.teamSlot - right.teamSlot ||
        left.sessionParticipantId.localeCompare(right.sessionParticipantId),
    )
  const teammate =
    teammateAssignments.length === 1
      ? personByParticipantId.get(
          teammateAssignments[0].sessionParticipantId,
        ) ?? null
      : null
  const opponents = opponentAssignments.flatMap((assignment) => {
    const opponent = personByParticipantId.get(
      assignment.sessionParticipantId,
    )
    return opponent === undefined ? [] : [opponent]
  })
  const uniqueAssignmentIds = new Set(
    assignments.map((assignment) => assignment.sessionParticipantId),
  )
  const assignmentSlots = new Set(
    assignments.map(
      (assignment) => `${assignment.teamSide}${assignment.teamSlot}`,
    ),
  )
  const completeDoublesAssignment =
    assignments.length === 4 &&
    uniqueAssignmentIds.size === 4 &&
    assignmentSlots.size === 4 &&
    ['A1', 'A2', 'B1', 'B2'].every((slot) => assignmentSlots.has(slot)) &&
    teammate !== null &&
    opponents.length === 2

  return {
    courtName: courtName ?? 'Không có dữ liệu sân',
    teammate,
    opponents,
    detailsIncomplete: courtName === null || !completeDoublesAssignment,
  }
}

function compareQueuedPlans(
  left: LiveSessionData['matchPlans'][number],
  right: LiveSessionData['matchPlans'][number],
): number {
  return (
    (left.queuePosition ?? Number.MAX_SAFE_INTEGER) -
      (right.queuePosition ?? Number.MAX_SAFE_INTEGER) ||
    left.createdAt.localeCompare(right.createdAt) ||
    left.id.localeCompare(right.id)
  )
}

function comparePlayingMatches(
  left: LiveSessionData['matches'][number],
  right: LiveSessionData['matches'][number],
): number {
  return (
    (left.startedAt ?? left.createdAt).localeCompare(
      right.startedAt ?? right.createdAt,
    ) || left.id.localeCompare(right.id)
  )
}

function personView(
  participant: SessionParticipantResponse,
  data: LiveSessionData,
): PlayerSessionPersonView {
  const player = data.players.find(
    (candidate) => candidate.id === participant.playerId,
  )
  return {
    sessionParticipantId: participant.id,
    participantCode: participant.participantCode,
    displayName: player?.displayName ?? 'Không có dữ liệu người chơi',
  }
}

export function composePlayerSessionViewModel(
  data: LiveSessionData,
  sessionParticipantId: string,
): PlayerSessionViewModel | null {
  const participant = data.participants.find(
    (candidate) => candidate.id === sessionParticipantId,
  )
  if (participant === undefined) {
    return null
  }

  const personByParticipantId = new Map(
    data.participants.map((candidate) => [
      candidate.id,
      personView(candidate, data),
    ]),
  )
  const base = {
    sessionId: data.session.id,
    sessionTitle: data.session.title,
    sessionStatus: data.session.status,
    participant: personView(participant, data),
  }

  if (participant.status === 'LEFT' || participant.status === 'PAUSED') {
    return {
      ...base,
      displayState: participant.status,
      activity: null,
      detailsIncomplete: false,
    }
  }

  const playingMatches = data.matches
    .filter(
      (match) =>
        match.status === 'PLAYING' &&
        match.participants.some(
          (assignment) =>
            assignment.sessionParticipantId === participant.id,
        ),
    )
    .toSorted(comparePlayingMatches)
  const playingMatch = playingMatches[0]
  if (playingMatch !== undefined) {
    const activity = resolveActivity(
      playingMatch.participants,
      participant.id,
      resolveCourtName(data, playingMatch.sessionCourtId),
      personByParticipantId,
    )
    return {
      ...base,
      displayState: 'PLAYING',
      activity,
      detailsIncomplete:
        playingMatches.length !== 1 || activity.detailsIncomplete,
    }
  }

  const queuedPlans = data.matchPlans
    .filter(
      (plan) =>
        plan.status === 'QUEUED' &&
        plan.participants.some(
          (assignment) =>
            assignment.sessionParticipantId === participant.id,
        ),
    )
    .toSorted(compareQueuedPlans)
  const queuedPlan = queuedPlans[0]
  if (queuedPlan !== undefined) {
    const activity = resolveActivity(
      queuedPlan.participants,
      participant.id,
      resolveCourtName(data, queuedPlan.sessionCourtId),
      personByParticipantId,
    )
    return {
      ...base,
      displayState: 'QUEUED',
      activity,
      detailsIncomplete: queuedPlans.length !== 1 || activity.detailsIncomplete,
    }
  }

  return {
    ...base,
    displayState: participant.status,
    activity: null,
    detailsIncomplete: participant.status === 'PLAYING',
  }
}
