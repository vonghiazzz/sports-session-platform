import type { PlayerSessionAccessResponse } from './contracts'
import { getJson } from './http'

function segment(value: string): string {
  return encodeURIComponent(value)
}

export function resolvePlayerSessionAccess(
  token: string,
  signal?: AbortSignal,
): Promise<PlayerSessionAccessResponse> {
  return getJson(`/api/player-session-access/${segment(token)}`, signal)
}
