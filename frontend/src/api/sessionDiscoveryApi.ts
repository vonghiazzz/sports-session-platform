import type { SessionResponse } from './contracts'
import { getJson } from './http'

export function getSessions(
  signal?: AbortSignal,
): Promise<readonly SessionResponse[]> {
  return getJson('/api/sessions', signal)
}
