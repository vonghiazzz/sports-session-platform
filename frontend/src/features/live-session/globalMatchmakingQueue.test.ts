import { describe, expect, it } from 'vitest'
import type {
  GlobalMatchmakingGenerationResponse,
  GlobalMatchmakingQueueRecommendationRequest,
  MatchPlanResponse,
  MatchRecommendationResponse,
} from '../../api/contracts'
import {
  buildGlobalMatchmakingQueueRequest,
  reconcileGlobalQueueOutcome,
} from './globalMatchmakingQueue'

const algorithmVersion =
  'fairness-anchor-level-session-count-rating-sum-v3'

function player(
  number: number,
  teamSide: 'A' | 'B',
  teamSlot: 1 | 2,
) {
  return {
    sessionParticipantId: `participant-${number}`,
    playerId: `player-${number}`,
    teamSide,
    teamSlot,
    waitingSince: '2026-09-02T09:30:00Z',
    waitingSeconds: 1800,
    sessionMatchesPlayed: 0,
    ratingValue: 25,
    uncertainty: 8.33,
    ratedMatches: 0,
    ratingBasis: 'INITIAL_PRIOR',
  } as const
}

function recommendation(
  sessionCourtId: string,
  firstParticipant: number,
): MatchRecommendationResponse {
  return {
    outcome: 'RECOMMENDED',
    algorithmVersion,
    evaluationTime: '2026-09-02T10:00:00Z',
    sessionId: 'session-1',
    sessionCourtId,
    sportCode: 'BADMINTON',
    matchFormat: 'DOUBLES',
    eligiblePlayerCount: 8,
    teamA: {
      slot1: player(firstParticipant, 'A', 1),
      slot2: player(firstParticipant + 3, 'A', 2),
    },
    teamB: {
      slot1: player(firstParticipant + 1, 'B', 1),
      slot2: player(firstParticipant + 2, 'B', 2),
    },
    teamARatingTotal: 50,
    teamBRatingTotal: 50,
    ratingDifference: 0,
    oldestWaitingSince: '2026-09-02T09:30:00Z',
  }
}

function expectedRecommendation(
  sessionCourtId = 'court-1',
  firstParticipant = 1,
): GlobalMatchmakingQueueRecommendationRequest {
  return {
    sessionCourtId,
    assignments: [
      {
        sessionParticipantId: `participant-${firstParticipant}`,
        teamSide: 'A',
        teamSlot: 1,
      },
      {
        sessionParticipantId: `participant-${firstParticipant + 3}`,
        teamSide: 'A',
        teamSlot: 2,
      },
      {
        sessionParticipantId: `participant-${firstParticipant + 1}`,
        teamSide: 'B',
        teamSlot: 1,
      },
      {
        sessionParticipantId: `participant-${firstParticipant + 2}`,
        teamSide: 'B',
        teamSlot: 2,
      },
    ],
  }
}

function plan(
  expected: GlobalMatchmakingQueueRecommendationRequest,
  overrides: Partial<MatchPlanResponse> = {},
): MatchPlanResponse {
  return {
    id: `plan-${expected.sessionCourtId}`,
    sessionId: 'session-1',
    sessionCourtId: expected.sessionCourtId,
    source: 'RECOMMENDATION',
    status: 'QUEUED',
    queuePosition: 1,
    startedMatchId: null,
    participants: expected.assignments.map((assignment, index) => ({
      id: `plan-participant-${index + 1}`,
      ...assignment,
    })),
    createdAt: '2026-09-02T10:00:01Z',
    startedAt: null,
    cancelledAt: null,
    updatedAt: '2026-09-02T10:00:01Z',
    version: 0,
    ...overrides,
  }
}

describe('global Matchmaking queue evidence', () => {
  it('keeps every target Court in backend order and sends only exact recommended assignments', () => {
    const first = recommendation('court-1', 1)
    const second = recommendation('court-3', 5)
    const preview: GlobalMatchmakingGenerationResponse = {
      outcome: 'RECOMMENDED',
      orchestrationVersion: 'global-greedy-available-unplanned-v1',
      selectionAlgorithmVersion: algorithmVersion,
      evaluationTime: '2026-09-02T10:00:00Z',
      sessionId: 'session-1',
      initialEligiblePlayerCount: 8,
      courtResults: [
        first,
        {
          outcome: 'UNAVAILABLE',
          algorithmVersion,
          evaluationTime: '2026-09-02T10:00:00Z',
          sessionId: 'session-1',
          sessionCourtId: 'court-2',
          sportCode: 'BADMINTON',
          matchFormat: 'DOUBLES',
          eligiblePlayerCount: 3,
          reason: 'INSUFFICIENT_ELIGIBLE_PLAYERS',
        },
        second,
      ],
      reason: null,
    }

    expect(buildGlobalMatchmakingQueueRequest(preview)).toEqual({
      orchestrationVersion: preview.orchestrationVersion,
      selectionAlgorithmVersion: preview.selectionAlgorithmVersion,
      targetCourtIds: ['court-1', 'court-2', 'court-3'],
      recommendations: [
        expectedRecommendation('court-1', 1),
        expectedRecommendation('court-3', 5),
      ],
    })
  })

  it('confirms only when every expected Court has an exact queued recommendation Plan', () => {
    const expected = [
      expectedRecommendation('court-1', 1),
      expectedRecommendation('court-2', 5),
    ]

    expect(
      reconcileGlobalQueueOutcome('session-1', expected, [
        plan(expected[1]),
        plan(expected[0]),
      ]),
    ).toBe('CONFIRMED')
    expect(
      reconcileGlobalQueueOutcome('session-1', expected, [plan(expected[0])]),
    ).toBe('PARTIAL')
    expect(reconcileGlobalQueueOutcome('session-1', expected, [])).toBe('NONE')
  })

  it('rejects a wrong Session, source, status, participant, or team slot as confirmation', () => {
    const expected = expectedRecommendation()
    const wrongParticipant = plan(expected, {
      participants: plan(expected).participants.map((participant, index) =>
        index === 0
          ? { ...participant, sessionParticipantId: 'somebody-else' }
          : participant,
      ),
    })
    const wrongSlot = plan(expected, {
      participants: plan(expected).participants.map((participant, index) =>
        index === 0 ? { ...participant, teamSlot: 2 } : participant,
      ),
    })

    for (const mismatchedPlan of [
      plan(expected, { sessionId: 'other-session' }),
      plan(expected, { source: 'MANUAL' }),
      plan(expected, { status: 'STARTED' }),
      wrongParticipant,
      wrongSlot,
    ]) {
      expect(
        reconcileGlobalQueueOutcome('session-1', [expected], [mismatchedPlan]),
      ).toBe('NONE')
    }
  })
})
