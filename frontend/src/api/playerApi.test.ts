import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  getPlayer,
  getPlayers,
  updatePlayerSkillLevel,
} from './playerApi'

afterEach(() => {
  vi.unstubAllGlobals()
})

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('Player API', () => {
  it('gets the full Player list from the exact path', async () => {
    const fetchMock = vi.fn(async () => jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)
    const signal = new AbortController().signal

    await getPlayers(undefined, signal)

    expect(fetchMock).toHaveBeenCalledWith('/api/players', expect.objectContaining({
      method: 'GET',
      signal,
    }))
  })

  it('trims and encodes the authoritative backend name search', async () => {
    const fetchMock = vi.fn(async () => jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)

    await getPlayers('  Nguyễn An/1  ')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/players?name=Nguy%E1%BB%85n%20An%2F1',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('gets one Player using an escaped path segment', async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ id: 'player/1' }))
    vi.stubGlobal('fetch', fetchMock)

    await getPlayer('player/1')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/players/player%2F1',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('puts the exact SkillLevel path and body once', async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ id: 'player/1' }))
    vi.stubGlobal('fetch', fetchMock)

    await updatePlayerSkillLevel(
      'player/1',
      'BADMINTON',
      { skillLevel: 'INTERMEDIATE_PLUS' },
    )

    expect(fetchMock).toHaveBeenCalledOnce()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/players/player%2F1/sports/BADMINTON/skill-level',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ skillLevel: 'INTERMEDIATE_PLUS' }),
      }),
    )
  })
})
