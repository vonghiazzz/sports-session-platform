import { afterEach, describe, expect, it, vi } from 'vitest'
import { createLiveSessionInput } from '../test/liveSessionFixtures'
import {
  checkInParticipant,
  disableSessionCourt,
  enableSessionCourt,
  leaveParticipant,
  pauseParticipant,
  resumeParticipant,
} from './liveSessionApi'

const input = createLiveSessionInput()
const sessionId = input.session.id
const participantId = input.participants[0].id
const sessionCourtId = input.sessionCourts[0].id

const actionCases = [
  {
    name: 'Check-In',
    execute: () => checkInParticipant(sessionId, participantId),
    path: `/api/sessions/${sessionId}/participants/${participantId}/check-in`,
    response: input.participants[0],
  },
  {
    name: 'Pause',
    execute: () => pauseParticipant(sessionId, participantId),
    path: `/api/sessions/${sessionId}/participants/${participantId}/pause`,
    response: input.participants[0],
  },
  {
    name: 'Resume',
    execute: () => resumeParticipant(sessionId, participantId),
    path: `/api/sessions/${sessionId}/participants/${participantId}/resume`,
    response: input.participants[0],
  },
  {
    name: 'Leave',
    execute: () => leaveParticipant(sessionId, participantId),
    path: `/api/sessions/${sessionId}/participants/${participantId}/leave`,
    response: input.participants[0],
  },
  {
    name: 'Disable Court',
    execute: () => disableSessionCourt(sessionId, sessionCourtId),
    path: `/api/sessions/${sessionId}/courts/${sessionCourtId}/disable`,
    response: input.sessionCourts[0],
  },
  {
    name: 'Enable Court',
    execute: () => enableSessionCourt(sessionId, sessionCourtId),
    path: `/api/sessions/${sessionId}/courts/${sessionCourtId}/enable`,
    response: input.sessionCourts[0],
  },
] as const

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('live Session action API', () => {
  it.each(actionCases)('$name uses POST without a request body', async (testCase) => {
    const fetchMock = vi.fn(async (_path: string, _init?: RequestInit) =>
      new Response(JSON.stringify(testCase.response), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(testCase.execute()).resolves.toEqual(testCase.response)

    expect(fetchMock).toHaveBeenCalledOnce()
    expect(fetchMock).toHaveBeenCalledWith(testCase.path, {
      method: 'POST',
      headers: { Accept: 'application/json' },
      signal: undefined,
    })
    const request = fetchMock.mock.calls[0]?.[1]
    expect(request).not.toHaveProperty('body')
    expect(request?.headers).not.toHaveProperty('Content-Type')
  })
})
