import { afterEach, describe, expect, it, vi } from 'vitest'
import { createLiveSessionInput } from '../test/liveSessionFixtures'
import {
  cancelSession,
  cancelMatch,
  checkInParticipant,
  completeSession,
  completeMatch,
  createManualMatch,
  disableSessionCourt,
  enableSessionCourt,
  leaveParticipant,
  pauseParticipant,
  resumeParticipant,
  startMatch,
} from './liveSessionApi'

const input = createLiveSessionInput()
const sessionId = input.session.id
const participantId = input.participants[0].id
const sessionCourtId = input.sessionCourts[0].id

const actionCases = [
  {
    name: 'Complete Session',
    execute: () => completeSession(sessionId),
    path: `/api/sessions/${sessionId}/complete`,
    response: { ...input.session, status: 'COMPLETED' as const },
  },
  {
    name: 'Cancel Session',
    execute: () => cancelSession(sessionId),
    path: `/api/sessions/${sessionId}/cancel`,
    response: { ...input.session, status: 'CANCELLED' as const },
  },
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

  it('creates a Manual Match with the exact A1, A2, B1, B2 request', async () => {
    const request = {
      sessionCourtId,
      participants: [
        { sessionParticipantId: 'participant-1', teamSide: 'A' as const, teamSlot: 1 },
        { sessionParticipantId: 'participant-2', teamSide: 'A' as const, teamSlot: 2 },
        { sessionParticipantId: 'participant-3', teamSide: 'B' as const, teamSlot: 1 },
        { sessionParticipantId: 'participant-4', teamSide: 'B' as const, teamSlot: 2 },
      ],
    }
    const response = input.matches[1]
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify(response), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(createManualMatch(sessionId, request)).resolves.toEqual(response)
    expect(fetchMock).toHaveBeenCalledWith(`/api/sessions/${sessionId}/matches`, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      signal: undefined,
      body: JSON.stringify(request),
    })
  })

  it('starts a Match with a bodyless POST', async () => {
    const response = input.matches[0]
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify(response), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(startMatch('match-created')).resolves.toEqual(response)
    expect(fetchMock).toHaveBeenCalledWith('/api/matches/match-created/start', {
      method: 'POST',
      headers: { Accept: 'application/json' },
      signal: undefined,
    })
  })

  it('completes a Match with the exact result JSON body', async () => {
    const request = {
      winnerTeam: 'A' as const,
      teamAScore: 21,
      teamBScore: 17,
    }
    const response = {
      ...input.matches[0],
      status: 'COMPLETED' as const,
      winnerTeam: 'A' as const,
      teamAScore: 21,
      teamBScore: 17,
      resultVersion: 1,
    }
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify(response), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(completeMatch('match-playing', request)).resolves.toEqual(response)
    expect(fetchMock).toHaveBeenCalledOnce()
    expect(fetchMock).toHaveBeenCalledWith('/api/matches/match-playing/complete', {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      signal: undefined,
      body: JSON.stringify(request),
    })
  })

  it('cancels a Match with a bodyless POST and parses the response', async () => {
    const response = {
      ...input.matches[1],
      status: 'CANCELLED' as const,
    }
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify(response), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(cancelMatch('match-created')).resolves.toEqual(response)
    expect(fetchMock).toHaveBeenCalledOnce()
    expect(fetchMock).toHaveBeenCalledWith('/api/matches/match-created/cancel', {
      method: 'POST',
      headers: { Accept: 'application/json' },
      signal: undefined,
    })
  })
})
