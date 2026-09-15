import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SessionResponse } from './contracts'
import { getSessions } from './sessionDiscoveryApi'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Session discovery API', () => {
  it('gets the ordered Session collection with one GET request', async () => {
    const response: readonly SessionResponse[] = []
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify(response), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const signal = new AbortController().signal

    await expect(getSessions(signal)).resolves.toEqual(response)
    expect(fetchMock).toHaveBeenCalledOnce()
    expect(fetchMock).toHaveBeenCalledWith('/api/sessions', {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal,
    })
  })
})
