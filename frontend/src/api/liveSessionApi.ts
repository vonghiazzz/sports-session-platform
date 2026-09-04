import type {
  CourtResponse,
  MatchResponse,
  PlayerResponse,
  SessionCourtResponse,
  SessionParticipantResponse,
  SessionResponse,
  VenueResponse,
} from './contracts'
import { getJson, postJson } from './http'

function segment(value: string): string {
  return encodeURIComponent(value)
}

export function getSession(
  sessionId: string,
  signal?: AbortSignal,
): Promise<SessionResponse> {
  return getJson(`/api/sessions/${segment(sessionId)}`, signal)
}

export function getVenue(
  venueId: string,
  signal?: AbortSignal,
): Promise<VenueResponse> {
  return getJson(`/api/venues/${segment(venueId)}`, signal)
}

export function getSessionCourts(
  sessionId: string,
  signal?: AbortSignal,
): Promise<readonly SessionCourtResponse[]> {
  return getJson(`/api/sessions/${segment(sessionId)}/courts`, signal)
}

export function getVenueCourts(
  venueId: string,
  signal?: AbortSignal,
): Promise<readonly CourtResponse[]> {
  return getJson(`/api/venues/${segment(venueId)}/courts`, signal)
}

export function getSessionParticipants(
  sessionId: string,
  signal?: AbortSignal,
): Promise<readonly SessionParticipantResponse[]> {
  return getJson(`/api/sessions/${segment(sessionId)}/participants`, signal)
}

export function getPlayers(
  signal?: AbortSignal,
): Promise<readonly PlayerResponse[]> {
  return getJson('/api/players', signal)
}

export function getSessionMatches(
  sessionId: string,
  signal?: AbortSignal,
): Promise<readonly MatchResponse[]> {
  return getJson(`/api/sessions/${segment(sessionId)}/matches`, signal)
}

function participantActionPath(
  sessionId: string,
  sessionParticipantId: string,
  action: 'check-in' | 'pause' | 'resume' | 'leave',
): string {
  return `/api/sessions/${segment(sessionId)}/participants/${segment(sessionParticipantId)}/${action}`
}

export function checkInParticipant(
  sessionId: string,
  sessionParticipantId: string,
): Promise<SessionParticipantResponse> {
  return postJson(
    participantActionPath(sessionId, sessionParticipantId, 'check-in'),
  )
}

export function pauseParticipant(
  sessionId: string,
  sessionParticipantId: string,
): Promise<SessionParticipantResponse> {
  return postJson(
    participantActionPath(sessionId, sessionParticipantId, 'pause'),
  )
}

export function resumeParticipant(
  sessionId: string,
  sessionParticipantId: string,
): Promise<SessionParticipantResponse> {
  return postJson(
    participantActionPath(sessionId, sessionParticipantId, 'resume'),
  )
}

export function leaveParticipant(
  sessionId: string,
  sessionParticipantId: string,
): Promise<SessionParticipantResponse> {
  return postJson(
    participantActionPath(sessionId, sessionParticipantId, 'leave'),
  )
}

function courtActionPath(
  sessionId: string,
  sessionCourtId: string,
  action: 'disable' | 'enable',
): string {
  return `/api/sessions/${segment(sessionId)}/courts/${segment(sessionCourtId)}/${action}`
}

export function disableSessionCourt(
  sessionId: string,
  sessionCourtId: string,
): Promise<SessionCourtResponse> {
  return postJson(courtActionPath(sessionId, sessionCourtId, 'disable'))
}

export function enableSessionCourt(
  sessionId: string,
  sessionCourtId: string,
): Promise<SessionCourtResponse> {
  return postJson(courtActionPath(sessionId, sessionCourtId, 'enable'))
}
