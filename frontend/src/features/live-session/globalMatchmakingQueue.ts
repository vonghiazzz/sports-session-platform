import type {
  AcceptMatchmakingAssignmentRequest,
  GlobalMatchmakingGenerationResponse,
  GlobalMatchmakingQueueRecommendationRequest,
  GlobalMatchmakingQueueRequest,
  MatchPlanResponse,
  MatchRecommendationResponse,
} from '../../api/contracts'

export type GlobalQueueReconciliation = 'CONFIRMED' | 'PARTIAL' | 'NONE'

function recommendationAssignments(
  recommendation: MatchRecommendationResponse,
): readonly AcceptMatchmakingAssignmentRequest[] {
  return [
    recommendation.teamA.slot1,
    recommendation.teamA.slot2,
    recommendation.teamB.slot1,
    recommendation.teamB.slot2,
  ].map((player) => ({
    sessionParticipantId: player.sessionParticipantId,
    teamSide: player.teamSide,
    teamSlot: player.teamSlot,
  }))
}

export function buildGlobalMatchmakingQueueRequest(
  preview: GlobalMatchmakingGenerationResponse,
): GlobalMatchmakingQueueRequest {
  return {
    orchestrationVersion: preview.orchestrationVersion,
    selectionAlgorithmVersion: preview.selectionAlgorithmVersion,
    targetCourtIds: preview.courtResults.map(
      (result) => result.sessionCourtId,
    ),
    recommendations: preview.courtResults
      .filter(
        (result): result is MatchRecommendationResponse =>
          result.outcome === 'RECOMMENDED',
      )
      .map((recommendation) => ({
        sessionCourtId: recommendation.sessionCourtId,
        assignments: recommendationAssignments(recommendation),
      })),
  }
}

function assignmentSignature(
  assignment: AcceptMatchmakingAssignmentRequest,
): string {
  return `${assignment.sessionParticipantId}:${assignment.teamSide}:${assignment.teamSlot}`
}

function matchesRecommendation(
  sessionId: string,
  expected: GlobalMatchmakingQueueRecommendationRequest,
  plan: MatchPlanResponse,
): boolean {
  if (
    plan.sessionId !== sessionId ||
    plan.sessionCourtId !== expected.sessionCourtId ||
    plan.status !== 'QUEUED' ||
    plan.source !== 'RECOMMENDATION' ||
    plan.participants.length !== expected.assignments.length
  ) {
    return false
  }

  const expectedAssignments = expected.assignments
    .map(assignmentSignature)
    .toSorted()
  const actualAssignments = plan.participants
    .map(assignmentSignature)
    .toSorted()

  return expectedAssignments.every(
    (assignment, index) => assignment === actualAssignments[index],
  )
}

export function reconcileGlobalQueueOutcome(
  sessionId: string,
  expectedRecommendations: readonly GlobalMatchmakingQueueRecommendationRequest[],
  currentMatchPlans: readonly MatchPlanResponse[],
): GlobalQueueReconciliation {
  const matchedCount = expectedRecommendations.filter((expected) =>
    currentMatchPlans.some((plan) =>
      matchesRecommendation(sessionId, expected, plan),
    ),
  ).length

  if (
    expectedRecommendations.length > 0 &&
    matchedCount === expectedRecommendations.length
  ) {
    return 'CONFIRMED'
  }
  return matchedCount > 0 ? 'PARTIAL' : 'NONE'
}
