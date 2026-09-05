import type {
  AddSessionCourtRequest,
  AddSessionParticipantRequest,
  CourtResponse,
  CreateCourtRequest,
  CreatePlayerRequest,
  CreateSessionRequest,
  CreateVenueRequest,
  PlayerResponse,
  SessionCourtResponse,
  SessionParticipantResponse,
  SessionResponse,
  VenueResponse,
} from './contracts'
import { getJson, postJson, postJsonWithBody } from './http'

function segment(value: string): string {
  return encodeURIComponent(value)
}

export function getVenues(
  signal?: AbortSignal,
): Promise<readonly VenueResponse[]> {
  return getJson('/api/venues', signal)
}

export function getSetupPlayers(
  signal?: AbortSignal,
): Promise<readonly PlayerResponse[]> {
  return getJson('/api/players', signal)
}

export function getSetupVenueCourts(
  venueId: string,
  signal?: AbortSignal,
): Promise<readonly CourtResponse[]> {
  return getJson(`/api/venues/${segment(venueId)}/courts`, signal)
}

export function createVenue(
  request: CreateVenueRequest,
): Promise<VenueResponse> {
  return postJsonWithBody('/api/venues', request)
}

export function createCourt(
  venueId: string,
  request: CreateCourtRequest,
): Promise<CourtResponse> {
  return postJsonWithBody(
    `/api/venues/${segment(venueId)}/courts`,
    request,
  )
}

export function createPlayer(
  request: CreatePlayerRequest,
): Promise<PlayerResponse> {
  return postJsonWithBody('/api/players', request)
}

export function createSession(
  request: CreateSessionRequest,
): Promise<SessionResponse> {
  return postJsonWithBody('/api/sessions', request)
}

export function getSetupSession(
  sessionId: string,
): Promise<SessionResponse> {
  return getJson(`/api/sessions/${segment(sessionId)}`)
}

export function getSetupSessionCourts(
  sessionId: string,
): Promise<readonly SessionCourtResponse[]> {
  return getJson(`/api/sessions/${segment(sessionId)}/courts`)
}

export function getSetupSessionParticipants(
  sessionId: string,
): Promise<readonly SessionParticipantResponse[]> {
  return getJson(`/api/sessions/${segment(sessionId)}/participants`)
}

export function addSessionCourt(
  sessionId: string,
  request: AddSessionCourtRequest,
): Promise<SessionCourtResponse> {
  return postJsonWithBody(
    `/api/sessions/${segment(sessionId)}/courts`,
    request,
  )
}

export function addSessionParticipant(
  sessionId: string,
  request: AddSessionParticipantRequest,
): Promise<SessionParticipantResponse> {
  return postJsonWithBody(
    `/api/sessions/${segment(sessionId)}/participants`,
    request,
  )
}

export function startSetupSession(sessionId: string): Promise<SessionResponse> {
  return postJson(`/api/sessions/${segment(sessionId)}/start`)
}
