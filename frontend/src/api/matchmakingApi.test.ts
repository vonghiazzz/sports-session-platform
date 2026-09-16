import { afterEach, describe, expect, it, vi } from 'vitest'
import type {
  AcceptMatchmakingRecommendationRequest,
  GlobalMatchmakingGenerationResponse,
  GlobalMatchmakingQueueRequest,
  GlobalMatchmakingQueueResponse,
  MatchmakingGenerationResponse,
  MatchPlanResponse,
  MatchResponse,
} from './contracts'
import {
  acceptMatchmakingRecommendation,
  generateGlobalMatchmakingPreview,
  generateMatchmakingRecommendation,
  queueGlobalMatchmakingRecommendations,
  queueMatchmakingRecommendation,
} from './matchmakingApi'

const sessionId = 'session/one'
const sessionCourtId = 'court two'
const algorithmVersion =
  'fairness-anchor-level-session-count-rating-sum-v3'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Matchmaking recommendation API', () => {
  it('generates the global preview through the exact Session-scoped bodyless endpoint', async () => {
    const response = {
      outcome: 'UNAVAILABLE',
      orchestrationVersion: 'global-greedy-available-unplanned-v1',
      selectionAlgorithmVersion: algorithmVersion,
      evaluationTime: '2026-09-02T10:00:00Z',
      sessionId,
      initialEligiblePlayerCount: 3,
      courtResults: [],
      reason: 'NO_ELIGIBLE_COURTS',
    } satisfies GlobalMatchmakingGenerationResponse

    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify(response), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    vi.stubGlobal('fetch', fetchMock)

    await expect(generateGlobalMatchmakingPreview(sessionId)).resolves.toEqual(
      response,
    )

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/sessions/session%2Fone/match-recommendations',
      {
        method: 'POST',
        headers: { Accept: 'application/json' },
        signal: undefined,
      },
    )
  })

  it('generates through the exact Court-scoped bodyless endpoint', async () => {
    const response = {
      outcome: 'UNAVAILABLE',
      algorithmVersion,
      evaluationTime: '2026-09-02T10:00:00Z',
      sessionId,
      sessionCourtId,
      sportCode: 'BADMINTON',
      matchFormat: 'DOUBLES',
      eligiblePlayerCount: 3,
      reason: 'INSUFFICIENT_ELIGIBLE_PLAYERS',
    } satisfies MatchmakingGenerationResponse

    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify(response), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    vi.stubGlobal('fetch', fetchMock)

    await expect(
      generateMatchmakingRecommendation(sessionId, sessionCourtId),
    ).resolves.toEqual(response)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/sessions/session%2Fone/courts/court%20two/match-recommendations',
      {
        method: 'POST',
        headers: { Accept: 'application/json' },
        signal: undefined,
      },
    )
  })

  it('queues a global preview through the exact Session-scoped endpoint and body', async () => {
    const request: GlobalMatchmakingQueueRequest = {
      orchestrationVersion: 'global-greedy-available-unplanned-v1',
      selectionAlgorithmVersion: algorithmVersion,
      targetCourtIds: ['court-1', 'court-unavailable'],
      recommendations: [
        {
          sessionCourtId: 'court-1',
          assignments: [
            { sessionParticipantId: 'p1', teamSide: 'A', teamSlot: 1 },
            { sessionParticipantId: 'p2', teamSide: 'A', teamSlot: 2 },
            { sessionParticipantId: 'p3', teamSide: 'B', teamSlot: 1 },
            { sessionParticipantId: 'p4', teamSide: 'B', teamSlot: 2 },
          ],
        },
      ],
    }
    const response = {
      sessionId,
      orchestrationVersion: request.orchestrationVersion,
      selectionAlgorithmVersion: request.selectionAlgorithmVersion,
      createdPlans: [],
    } satisfies GlobalMatchmakingQueueResponse
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify(response), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      queueGlobalMatchmakingRecommendations(sessionId, request),
    ).resolves.toEqual(response)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/sessions/session%2Fone/match-recommendations/queue',
      {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        signal: undefined,
        body: JSON.stringify(request),
      },
    )
  })

  it('accepts with exact recommendation evidence and no Match source', async () => {
    const request: AcceptMatchmakingRecommendationRequest = {
      algorithmVersion,
      assignments: [
        { sessionParticipantId: 'p1', teamSide: 'A', teamSlot: 1 },
        { sessionParticipantId: 'p2', teamSide: 'A', teamSlot: 2 },
        { sessionParticipantId: 'p3', teamSide: 'B', teamSlot: 1 },
        { sessionParticipantId: 'p4', teamSide: 'B', teamSlot: 2 },
      ],
    }

    const response = { id: 'match-1' } as MatchResponse

    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify(response), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    vi.stubGlobal('fetch', fetchMock)

    await expect(
      acceptMatchmakingRecommendation(sessionId, sessionCourtId, request),
    ).resolves.toEqual(response)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/sessions/session%2Fone/courts/court%20two/match-recommendations/accept',
      {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        signal: undefined,
        body: JSON.stringify(request),
      },
    )

    expect(request).not.toHaveProperty('source')
  })

  it('queues with exact recommendation evidence and no client-controlled Plan state', async () => {
    const request: AcceptMatchmakingRecommendationRequest = {
      algorithmVersion,
      assignments: [
        { sessionParticipantId: 'p1', teamSide: 'A', teamSlot: 1 },
        { sessionParticipantId: 'p2', teamSide: 'A', teamSlot: 2 },
        { sessionParticipantId: 'p3', teamSide: 'B', teamSlot: 1 },
        { sessionParticipantId: 'p4', teamSide: 'B', teamSlot: 2 },
      ],
    }

    const response = {
      id: 'match-plan-1',
    } as MatchPlanResponse

    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify(response), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    vi.stubGlobal('fetch', fetchMock)

    await expect(
      queueMatchmakingRecommendation(sessionId, sessionCourtId, request),
    ).resolves.toEqual(response)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/sessions/session%2Fone/courts/court%20two/match-recommendations/queue',
      {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        signal: undefined,
        body: JSON.stringify(request),
      },
    )

    expect(request).not.toHaveProperty('source')
    expect(request).not.toHaveProperty('status')
    expect(request).not.toHaveProperty('queuePosition')
  })
})
