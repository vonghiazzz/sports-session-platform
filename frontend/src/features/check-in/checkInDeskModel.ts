import type {
  PlayerResponse,
  SessionParticipantResponse,
} from '../../api/contracts'
import { normalizePlayerSearch } from '../live-session/peopleOperations'

export interface CheckInParticipantView {
  readonly sessionParticipantId: string
  readonly participantCode: number
  readonly displayName: string
  readonly status: SessionParticipantResponse['status']
  readonly checkedInAt: string | null
  readonly playerDataUnavailable: boolean
}

export interface CheckInSummary {
  readonly total: number
  readonly registered: number
  readonly checkedIn: number
}

export function composeCheckInParticipants(
  participants: readonly SessionParticipantResponse[],
  players: readonly PlayerResponse[],
): readonly CheckInParticipantView[] {
  const playerById = new Map(players.map((player) => [player.id, player]))

  return participants
    .map((participant) => {
      const player = playerById.get(participant.playerId)
      return {
        sessionParticipantId: participant.id,
        participantCode: participant.participantCode,
        displayName: player?.displayName ?? 'Không có dữ liệu người chơi',
        status: participant.status,
        checkedInAt: participant.checkedInAt,
        playerDataUnavailable: player === undefined,
      }
    })
    .sort(
      (left, right) =>
        left.participantCode - right.participantCode ||
        left.sessionParticipantId.localeCompare(right.sessionParticipantId),
    )
}

export function filterCheckInParticipants(
  participants: readonly CheckInParticipantView[],
  search: string,
): readonly CheckInParticipantView[] {
  const trimmedSearch = search.trim()
  if (trimmedSearch.length === 0) {
    return participants
  }

  const codeMatch = /^#?(\d+)$/.exec(trimmedSearch)
  if (codeMatch !== null) {
    const participantCode = Number(codeMatch[1])
    return participants.filter(
      (participant) => participant.participantCode === participantCode,
    )
  }

  const normalizedSearch = normalizePlayerSearch(trimmedSearch)
  return participants.filter((participant) =>
    normalizePlayerSearch(participant.displayName).includes(normalizedSearch),
  )
}

export function summarizeCheckInParticipants(
  participants: readonly CheckInParticipantView[],
): CheckInSummary {
  return {
    total: participants.length,
    registered: participants.filter(
      (participant) => participant.status === 'REGISTERED',
    ).length,
    checkedIn: participants.filter(
      (participant) => participant.checkedInAt !== null,
    ).length,
  }
}
