import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  addSessionCourt,
  addSessionParticipant,
  createCourt,
  createPlayer,
  createSession,
  createVenue,
  startSetupSession,
} from './sessionSetupApi'

afterEach(() => {
  vi.unstubAllGlobals()
})

function response(body: object) {
  return new Response(JSON.stringify(body), {
    status: 201,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('Session Setup mutation API', () => {
  it('uses the exact resource creation contracts', async () => {
    const fetchMock = vi.fn(async () => response({ id: 'created-id' }))
    vi.stubGlobal('fetch', fetchMock)

    await createVenue({ name: 'Nhà thi đấu A', locationText: 'Quận 1', active: true })
    await createCourt('venue/1', { name: 'Sân 1', sport: 'BADMINTON', active: true })
    await createPlayer({
      displayName: 'An',
      sport: 'BADMINTON',
      skillLevel: 'INTERMEDIATE_PLUS',
    })

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/venues', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ name: 'Nhà thi đấu A', locationText: 'Quận 1', active: true }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/venues/venue%2F1/courts', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ name: 'Sân 1', sport: 'BADMINTON', active: true }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/players', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        displayName: 'An',
        sport: 'BADMINTON',
        skillLevel: 'INTERMEDIATE_PLUS',
      }),
    }))
  })

  it('sends the exact Session, Court, and Participant allocation payloads', async () => {
    const fetchMock = vi.fn(async () => response({ id: 'created-id' }))
    vi.stubGlobal('fetch', fetchMock)
    const sessionRequest = {
      venueId: 'venue-1',
      title: 'Phiên tối',
      sport: 'BADMINTON' as const,
      matchFormat: 'DOUBLES' as const,
      plannedStartAt: '2026-09-05T11:00:00.000Z',
      plannedEndAt: '2026-09-05T13:00:00.000Z',
    }

    await createSession(sessionRequest)
    await addSessionCourt('session-1', { courtId: 'court-1' })
    await addSessionParticipant('session-1', { playerId: 'player-1' })
    await startSetupSession('session-1')

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/sessions', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify(sessionRequest),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/sessions/session-1/courts', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ courtId: 'court-1' }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/sessions/session-1/participants', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ playerId: 'player-1' }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/sessions/session-1/start', {
      method: 'POST',
      headers: { Accept: 'application/json' },
      signal: undefined,
    })
  })
})
