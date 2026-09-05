import type {
  AcceptMatchmakingRecommendationRequest,
  MatchmakingGenerationResponse,
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
