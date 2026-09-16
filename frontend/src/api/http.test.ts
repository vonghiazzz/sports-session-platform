import { afterEach, describe, expect, it, vi } from 'vitest'
import { getJson, postJson, postJsonWithBody } from './http'

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

  it('preserves bodyless POST headers without a body or Content-Type', async () => {
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify({ status: 'ok' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(postJson('/api/actions/run')).resolves.toEqual({ status: 'ok' })
    expect(fetchMock).toHaveBeenCalledWith('/api/actions/run', {
      method: 'POST',
      headers: { Accept: 'application/json' },
      signal: undefined,
    })
  })
})

describe('postJsonWithBody', () => {
  it('sends exact JSON with POST headers and parses the response', async () => {
    const requestBody = {
      sessionCourtId: 'session-court-2',
      participants: [
        { sessionParticipantId: 'participant-1', teamSide: 'A', teamSlot: 1 },
      ],
    }
    const responseBody = { id: 'match-created', status: 'CREATED' }
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify(responseBody), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      postJsonWithBody('/api/sessions/session-1/matches', requestBody),
    ).resolves.toEqual(responseBody)
    expect(fetchMock).toHaveBeenCalledOnce()
    expect(fetchMock).toHaveBeenCalledWith('/api/sessions/session-1/matches', {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      signal: undefined,
      body: JSON.stringify(requestBody),
    })
  })

  it('preserves structured HttpError details', async () => {
    const backendError = {
      timestamp: '2026-09-02T10:00:00Z',
      status: 409,
      error: 'Conflict',
      message: 'Live resources changed',
      path: '/api/sessions/session-1/matches',
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
      postJsonWithBody('/api/sessions/session-1/matches', { value: 'test' }),
    ).rejects.toMatchObject({
      name: 'HttpError',
      status: 409,
      apiError: backendError,
    })
  })

  it('performs one fetch when the network outcome is unknown', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      postJsonWithBody('/api/sessions/session-1/matches', { value: 'test' }),
    ).rejects.toThrow('Failed to fetch')
    expect(fetchMock).toHaveBeenCalledOnce()
  })
})
