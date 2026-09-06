import type {
  MatchPlanResponse,
  MoveMatchPlanRequest,
  ReorderMatchPlanRequest,
  SaveMatchPlanRequest,
  StartedMatchPlanResponse,
} from './contracts'
import { getJson, postJson, postJsonWithBody, putJsonWithBody } from './http'

function segment(value: string): string {
  return encodeURIComponent(value)
}

export function getSessionMatchPlans(
  sessionId: string,
  signal?: AbortSignal,
): Promise<readonly MatchPlanResponse[]> {
  return getJson(`/api/sessions/${segment(sessionId)}/match-plans`, signal)
}

export function createMatchPlan(
  sessionId: string,
  sessionCourtId: string,
  request: SaveMatchPlanRequest,
): Promise<MatchPlanResponse> {
  return postJsonWithBody(
    `/api/sessions/${segment(sessionId)}/courts/${segment(sessionCourtId)}/match-plans`,
    request,
  )
}

export function updateMatchPlan(
  matchPlanId: string,
  request: SaveMatchPlanRequest,
): Promise<MatchPlanResponse> {
  return putJsonWithBody(`/api/match-plans/${segment(matchPlanId)}`, request)
}

export function moveMatchPlan(
  matchPlanId: string,
  request: MoveMatchPlanRequest,
): Promise<MatchPlanResponse> {
  return postJsonWithBody(
    `/api/match-plans/${segment(matchPlanId)}/move`,
    request,
  )
}

export function reorderMatchPlan(
  matchPlanId: string,
  request: ReorderMatchPlanRequest,
): Promise<MatchPlanResponse> {
  return postJsonWithBody(
    `/api/match-plans/${segment(matchPlanId)}/reorder`,
    request,
  )
}

export function cancelMatchPlan(
  matchPlanId: string,
): Promise<MatchPlanResponse> {
  return postJson(`/api/match-plans/${segment(matchPlanId)}/cancel`)
}

export function startMatchPlan(
  matchPlanId: string,
): Promise<StartedMatchPlanResponse> {
  return postJson(`/api/match-plans/${segment(matchPlanId)}/start`)
}
