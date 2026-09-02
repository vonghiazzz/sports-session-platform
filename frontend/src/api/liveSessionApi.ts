import type {
  CourtResponse,
  MatchResponse,
  PlayerResponse,
  SessionCourtResponse,
  SessionParticipantResponse,
  SessionResponse,
  VenueResponse,
} from './contracts'
import { getJson } from './http'

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
