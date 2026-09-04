import { afterEach, describe, expect, it, vi } from 'vitest'
import { getJson, postJson } from './http'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('getJson', () => {
  it('preserves a parseable backend ApiError and HTTP status', async () => {
    const backendError = {
      timestamp: '2026-09-02T10:00:00Z',
      status: 404,
      error: 'Not Found',
      message: 'Session not found',
      path: '/api/sessions/missing',
      fieldErrors: {},
    }
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify(backendError), {
          status: 404,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )

    const request = getJson('/api/sessions/missing')

    await expect(request).rejects.toMatchObject({
      name: 'HttpError',
      status: 404,
      message: 'Session not found',
      apiError: backendError,
    })
  })

  it('uses a safe fallback when an error response is not ApiError JSON', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('service unavailable', { status: 503 })),
    )

    await expect(getJson('/api/sessions/unavailable')).rejects.toMatchObject({
      status: 503,
      message: 'Request failed with status 503',
    })
  })
})

describe('postJson', () => {
  it('preserves a backend ApiError for a rejected business action', async () => {
    const backendError = {
      timestamp: '2026-09-02T10:00:00Z',
      status: 409,
      error: 'Conflict',
      message: 'Participant cannot pause from status PAUSED',
      path: '/api/sessions/session-1/participants/participant-1/pause',
      fieldErrors: {},
    }
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify(backendError), {
          status: 409,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )

    await expect(
      postJson('/api/sessions/session-1/participants/participant-1/pause'),
    ).rejects.toMatchObject({
      name: 'HttpError',
      status: 409,
      apiError: backendError,
    })
  })

  it('does not retry a POST after a network failure', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      postJson('/api/sessions/session-1/courts/session-court-1/disable'),
    ).rejects.toThrow('Failed to fetch')
    expect(fetchMock).toHaveBeenCalledOnce()
  })
})
