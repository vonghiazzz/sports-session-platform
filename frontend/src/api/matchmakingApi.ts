import type {
  AcceptMatchmakingRecommendationRequest,
  GlobalMatchmakingGenerationResponse,
  MatchmakingGenerationResponse,
  MatchPlanResponse,
  MatchResponse,
} from './contracts'
import { postJson, postJsonWithBody } from './http'

function segment(value: string): string {
  return encodeURIComponent(value)
}

function recommendationPath(
  sessionId: string,
  sessionCourtId: string,
): string {
  return `/api/sessions/${segment(sessionId)}/courts/${segment(sessionCourtId)}/match-recommendations`
}

export function generateGlobalMatchmakingPreview(
  sessionId: string,
): Promise<GlobalMatchmakingGenerationResponse> {
  return postJson(
    `/api/sessions/${segment(sessionId)}/match-recommendations`,
  )
}

export function generateMatchmakingRecommendation(
  sessionId: string,
  sessionCourtId: string,
): Promise<MatchmakingGenerationResponse> {
  return postJson(recommendationPath(sessionId, sessionCourtId))
}

export function acceptMatchmakingRecommendation(
  sessionId: string,
  sessionCourtId: string,
  request: AcceptMatchmakingRecommendationRequest,
): Promise<MatchResponse> {
  return postJsonWithBody(
    `${recommendationPath(sessionId, sessionCourtId)}/accept`,
    request,
  )
}

export function queueMatchmakingRecommendation(
  sessionId: string,
  sessionCourtId: string,
  request: AcceptMatchmakingRecommendationRequest,
): Promise<MatchPlanResponse> {
  return postJsonWithBody(
    `${recommendationPath(sessionId, sessionCourtId)}/queue`,
    request,
  )
}
