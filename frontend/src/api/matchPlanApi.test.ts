import { afterEach, describe, expect, it, vi } from 'vitest'
import type { MatchPlanResponse, SaveMatchPlanRequest } from './contracts'
import {
  cancelMatchPlan,
  createMatchPlan,
  getSessionMatchPlans,
  moveMatchPlan,
  reorderMatchPlan,
  startMatchPlan,
  updateMatchPlan,
} from './matchPlanApi'

const assignments: SaveMatchPlanRequest = {
  participants: [
    { sessionParticipantId: 'participant-1', teamSide: 'A', teamSlot: 1 },
    { sessionParticipantId: 'participant-2', teamSide: 'A', teamSlot: 2 },
    { sessionParticipantId: 'participant-3', teamSide: 'B', teamSlot: 1 },
    { sessionParticipantId: 'participant-4', teamSide: 'B', teamSlot: 2 },
  ],
}

const plan: MatchPlanResponse = {
  id: 'plan-1',
  sessionId: 'session-1',
  sessionCourtId: 'session-court-1',
  source: 'MANUAL',
  status: 'QUEUED',
  queuePosition: 1,
  startedMatchId: null,
  participants: assignments.participants.map((participant, index) => ({
    id: `assignment-${index + 1}`,
    ...participant,
  })),
  createdAt: '2026-09-06T01:00:00Z',
  startedAt: null,
  cancelledAt: null,
  updatedAt: '2026-09-06T01:00:00Z',
  version: 0,
}

afterEach(() => vi.unstubAllGlobals())

function respond(body: unknown, status = 200) {
  const fetchMock = vi.fn(async () =>
    new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('MatchPlan API', () => {
  it('reads Session MatchPlans from the exact endpoint', async () => {
    const fetchMock = respond([plan])
    await expect(getSessionMatchPlans('session/1')).resolves.toEqual([plan])
    expect(fetchMock).toHaveBeenCalledWith('/api/sessions/session%2F1/match-plans', {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal: undefined,
    })
  })

  it('creates a plan with only the exact four assignments', async () => {
    const fetchMock = respond(plan, 201)
    await createMatchPlan('session-1', 'court-1', assignments)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/sessions/session-1/courts/court-1/match-plans',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify(assignments),
      }),
    )
  })

  it('updates all assignments with PUT', async () => {
    const fetchMock = respond(plan)
    await updateMatchPlan('plan-1', assignments)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/match-plans/plan-1',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify(assignments),
      }),
    )
  })

  it('moves to the exact target Session Court', async () => {
    const fetchMock = respond(plan)
    const request = { targetSessionCourtId: 'court-2' }
    await moveMatchPlan('plan-1', request)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/match-plans/plan-1/move',
      expect.objectContaining({ method: 'POST', body: JSON.stringify(request) }),
    )
  })

  it('reorders to the exact target position', async () => {
    const fetchMock = respond(plan)
    const request = { targetPosition: 2 }
    await reorderMatchPlan('plan-1', request)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/match-plans/plan-1/reorder',
      expect.objectContaining({ method: 'POST', body: JSON.stringify(request) }),
    )
  })

  it('cancels through a bodyless POST', async () => {
    const fetchMock = respond({ ...plan, status: 'CANCELLED' })
    await cancelMatchPlan('plan-1')
    expect(fetchMock).toHaveBeenCalledWith('/api/match-plans/plan-1/cancel', {
      method: 'POST',
      headers: { Accept: 'application/json' },
      signal: undefined,
    })
  })

  it('starts through a bodyless POST and preserves the wrapper response', async () => {
    const response = { matchPlan: { ...plan, status: 'STARTED' }, match: { id: 'match-1' } }
    const fetchMock = respond(response)
    await expect(startMatchPlan('plan-1')).resolves.toEqual(response)
    expect(fetchMock).toHaveBeenCalledWith('/api/match-plans/plan-1/start', {
      method: 'POST',
      headers: { Accept: 'application/json' },
      signal: undefined,
    })
  })
})
