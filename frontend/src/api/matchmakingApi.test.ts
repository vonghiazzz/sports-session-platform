import { afterEach, describe, expect, it, vi } from 'vitest'
import type {
  AcceptMatchmakingRecommendationRequest,
  MatchmakingGenerationResponse,
  MatchResponse,
} from './contracts'
import {
  acceptMatchmakingRecommendation,
  generateMatchmakingRecommendation,
} from './matchmakingApi'

const sessionId = 'session/one'
const sessionCourtId = 'court two'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Matchmaking recommendation API', () => {
  it('generates through the exact Court-scoped bodyless endpoint', async () => {
    const response = {
      outcome: 'UNAVAILABLE',
      algorithmVersion: 'fairness-anchor-rating-sum-v1',
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

  it('accepts with exact recommendation evidence and no Match source', async () => {
    const request: AcceptMatchmakingRecommendationRequest = {
      algorithmVersion: 'fairness-anchor-rating-sum-v1',
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
})
