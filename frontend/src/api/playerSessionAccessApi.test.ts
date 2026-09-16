import { afterEach, describe, expect, it, vi } from 'vitest'
import { resolvePlayerSessionAccess } from './playerSessionAccessApi'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Player Session access API', () => {
  it('resolves an opaque token to the internal Session locator', async () => {
    const response = {
      sessionId: '68b18f04-51f2-40eb-bbab-d551003574aa',
      sessionParticipantId: '6634a800-982c-4b1a-ad5a-ae2522e39d40',
    }
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify(response), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      resolvePlayerSessionAccess('token/with-reserved-character'),
    ).resolves.toEqual(response)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/player-session-access/token%2Fwith-reserved-character',
      {
        method: 'GET',
        headers: { Accept: 'application/json' },
        signal: undefined,
      },
    )
  })
})
