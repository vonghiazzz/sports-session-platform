export type UUID = string
export type ISOInstant = string

export type SportCode = 'BADMINTON'
export type MatchFormat = 'DOUBLES'
export type SessionStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type ParticipantStatus =
  | 'REGISTERED'
  | 'WAITING'
  | 'PLAYING'
  | 'PAUSED'
  | 'LEFT'
export type SessionCourtStatus = 'AVAILABLE' | 'PLAYING' | 'UNAVAILABLE'
export type SkillLevel =
  | 'WEAK'
  | 'WEAK_PLUS'
  | 'INTERMEDIATE_MINUS'
  | 'INTERMEDIATE'
  | 'INTERMEDIATE_PLUS'
  | 'GOOD'
export type MatchStatus = 'CREATED' | 'PLAYING' | 'COMPLETED' | 'CANCELLED'
export type MatchSource = 'MANUAL' | 'RECOMMENDATION' | 'MODIFIED_RECOMMENDATION'
export type TeamSide = 'A' | 'B'

export interface SessionResponse {
  readonly id: UUID
  readonly venueId: UUID
  readonly title: string
  readonly sport: SportCode
  readonly matchFormat: MatchFormat
  readonly plannedStartAt: ISOInstant
  readonly plannedEndAt: ISOInstant
  readonly status: SessionStatus
  readonly startedAt: ISOInstant | null
  readonly completedAt: ISOInstant | null
  readonly cancelledAt: ISOInstant | null
  readonly version: number
  readonly createdAt: ISOInstant
  readonly updatedAt: ISOInstant
}

export interface VenueResponse {
  readonly id: UUID
  readonly name: string
  readonly locationText: string | null
  readonly active: boolean
  readonly createdAt: ISOInstant
  readonly updatedAt: ISOInstant
}

export interface SessionParticipantResponse {
  readonly id: UUID
  readonly sessionId: UUID
  readonly playerId: UUID
  readonly status: ParticipantStatus
  readonly joinedAt: ISOInstant
  readonly checkedInAt: ISOInstant | null
  readonly waitingSince: ISOInstant | null
  readonly pausedAt: ISOInstant | null
  readonly totalPausedSeconds: number
  readonly leftAt: ISOInstant | null
  readonly version: number
  readonly createdAt: ISOInstant
  readonly updatedAt: ISOInstant
}

export interface PlayerSportProfileResponse {
  readonly id: UUID
  readonly sport: SportCode
  readonly skillLevel: SkillLevel
  readonly createdAt: ISOInstant
  readonly updatedAt: ISOInstant
}

export interface PlayerResponse {
  readonly id: UUID
  readonly displayName: string
  readonly sportProfiles: readonly PlayerSportProfileResponse[]
  readonly createdAt: ISOInstant
  readonly updatedAt: ISOInstant
}

export interface SessionCourtResponse {
  readonly id: UUID
  readonly sessionId: UUID
  readonly courtId: UUID
  readonly status: SessionCourtStatus
  readonly addedAt: ISOInstant
  readonly version: number
  readonly createdAt: ISOInstant
  readonly updatedAt: ISOInstant
}

export interface CourtResponse {
  readonly id: UUID
  readonly venueId: UUID
  readonly name: string
  readonly sport: SportCode
  readonly active: boolean
  readonly createdAt: ISOInstant
  readonly updatedAt: ISOInstant
}

export interface MatchParticipantResponse {
  readonly sessionParticipantId: UUID
  readonly teamSide: TeamSide
  readonly teamSlot: number
}

export interface MatchParticipantRequest {
  readonly sessionParticipantId: UUID
  readonly teamSide: TeamSide
  readonly teamSlot: number
}

export interface CreateManualMatchRequest {
  readonly sessionCourtId: UUID
  readonly participants: readonly MatchParticipantRequest[]
}

export interface CompleteMatchRequest {
  readonly winnerTeam: TeamSide
  readonly teamAScore: number | null
  readonly teamBScore: number | null
}

export interface MatchResponse {
  readonly id: UUID
  readonly sessionId: UUID
  readonly sessionCourtId: UUID
  readonly status: MatchStatus
  readonly source: MatchSource
  readonly winnerTeam: TeamSide | null
  readonly teamAScore: number | null
  readonly teamBScore: number | null
  readonly resultVersion: number
  readonly participants: readonly MatchParticipantResponse[]
  readonly createdAt: ISOInstant
  readonly startedAt: ISOInstant | null
  readonly completedAt: ISOInstant | null
  readonly cancelledAt: ISOInstant | null
  readonly updatedAt: ISOInstant
  readonly version: number
}

export interface ApiError {
  readonly timestamp: ISOInstant
  readonly status: number
  readonly error: string
  readonly message: string
  readonly path: string
  readonly fieldErrors: Readonly<Record<string, string>>
}
