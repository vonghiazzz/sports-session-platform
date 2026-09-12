import type {
  MatchFormat,
  PlayerRatingHistoryResponse,
  PlayerResponse,
  SportCode,
  UpdatePlayerSkillLevelRequest,
} from './contracts'
import { getJson, putJsonWithBody } from './http'

function segment(value: string): string {
  return encodeURIComponent(value)
}

export function getPlayers(
  name?: string,
  signal?: AbortSignal,
): Promise<readonly PlayerResponse[]> {
  const normalizedName = name?.trim() ?? ''
  const path = normalizedName.length === 0
    ? '/api/players'
    : `/api/players?name=${encodeURIComponent(normalizedName)}`
  return getJson(path, signal)
}

export function getPlayer(
  playerId: string,
  signal?: AbortSignal,
): Promise<PlayerResponse> {
  return getJson(`/api/players/${segment(playerId)}`, signal)
}

export function getPlayerRatingHistory(
  playerId: string,
  sportCode: SportCode,
  matchFormat: MatchFormat,
  signal?: AbortSignal,
): Promise<PlayerRatingHistoryResponse> {
  return getJson(
    `/api/players/${segment(playerId)}/sports/${segment(sportCode)}/ratings/${segment(matchFormat)}/history`,
    signal,
  )
}

export function updatePlayerSkillLevel(
  playerId: string,
  sportCode: SportCode,
  request: UpdatePlayerSkillLevelRequest,
): Promise<PlayerResponse> {
  return putJsonWithBody(
    `/api/players/${segment(playerId)}/sports/${segment(sportCode)}/skill-level`,
    request,
  )
}
