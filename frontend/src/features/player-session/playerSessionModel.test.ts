import { describe, expect, it } from 'vitest'
import type { MatchPlanResponse } from '../../api/contracts'
import { createLiveSessionInput } from '../../test/liveSessionFixtures'
import { composePlayerSessionViewModel } from './playerSessionModel'

function queuedPlan(
  participants: MatchPlanResponse['participants'] = [
    {
      id: 'plan-participant-1',
      sessionParticipantId: 'participant-1',
      teamSide: 'A',
      teamSlot: 1,
    },
    {
      id: 'plan-participant-2',
      sessionParticipantId: 'participant-2',
      teamSide: 'A',
      teamSlot: 2,
    },
    {
      id: 'plan-participant-3',
      sessionParticipantId: 'participant-3',
      teamSide: 'B',
      teamSlot: 1,
    },
    {
      id: 'plan-participant-4',
      sessionParticipantId: 'participant-4',
      teamSide: 'B',
      teamSlot: 2,
    },
  ],
): MatchPlanResponse {
  return {
    id: 'plan-1',
    sessionId: 'session-1',
    sessionCourtId: 'session-court-2',
    source: 'MANUAL',
    status: 'QUEUED',
    queuePosition: 1,
    startedMatchId: null,
    participants,
    createdAt: '2026-09-02T09:55:00Z',
    startedAt: null,
    cancelledAt: null,
    updatedAt: '2026-09-02T09:55:00Z',
    version: 0,
  }
}

describe('composePlayerSessionViewModel', () => {
  it('keeps REGISTERED and WAITING as authoritative display states', () => {
    const input = createLiveSessionInput()

    expect(
      composePlayerSessionViewModel(input, 'participant-3')?.displayState,
    ).toBe('REGISTERED')
    expect(
      composePlayerSessionViewModel(input, 'participant-1')?.displayState,
    ).toBe('WAITING')
  })

  it('derives a QUEUED doubles assignment by SessionParticipant UUID', () => {
    const input = createLiveSessionInput()
    const model = composePlayerSessionViewModel(
      { ...input, matchPlans: [queuedPlan()] },
      'participant-1',
    )

    expect(model?.displayState).toBe('QUEUED')
    expect(model?.activity).toMatchObject({
      courtName: 'Court Two',
      teammate: {
        sessionParticipantId: 'participant-2',
        participantCode: 2,
        displayName: 'Bao Tran',
      },
      detailsIncomplete: false,
    })
    expect(model?.activity?.opponents).toEqual([
      {
        sessionParticipantId: 'participant-3',
        participantCode: 3,
        displayName: 'Chi Le',
      },
      {
        sessionParticipantId: 'participant-4',
        participantCode: 4,
        displayName: 'Dung Pham',
      },
    ])
  })

  it('derives a PLAYING doubles assignment before a queued plan', () => {
    const input = createLiveSessionInput()
    const planForPlayingParticipant = queuedPlan([
      {
        id: 'queued-5',
        sessionParticipantId: 'participant-5',
        teamSide: 'A',
        teamSlot: 1,
      },
      {
        id: 'queued-1',
        sessionParticipantId: 'participant-1',
        teamSide: 'A',
        teamSlot: 2,
      },
      {
        id: 'queued-2',
        sessionParticipantId: 'participant-2',
        teamSide: 'B',
        teamSlot: 1,
      },
      {
        id: 'queued-3',
        sessionParticipantId: 'participant-3',
        teamSide: 'B',
        teamSlot: 2,
      },
    ])

    const model = composePlayerSessionViewModel(
      { ...input, matchPlans: [planForPlayingParticipant] },
      'participant-5',
    )

    expect(model?.displayState).toBe('PLAYING')
    expect(model?.activity?.courtName).toBe('Court One')
    expect(model?.activity?.teammate).toMatchObject({
      sessionParticipantId: 'participant-6',
      participantCode: 6,
      displayName: 'Hanh Bui',
    })
    expect(model?.activity?.opponents.map((opponent) => opponent.sessionParticipantId))
      .toEqual(['participant-7', 'participant-8'])
  })

  it.each(['PAUSED', 'LEFT'] as const)(
    'gives %s precedence over stale MatchPlan membership',
    (status) => {
      const input = createLiveSessionInput()
      const participants = input.participants.map((participant) =>
        participant.id === 'participant-1'
          ? { ...participant, status }
          : participant,
      )

      const model = composePlayerSessionViewModel(
        { ...input, participants, matchPlans: [queuedPlan()] },
        'participant-1',
      )

      expect(model?.displayState).toBe(status)
      expect(model?.activity).toBeNull()
    },
  )

  it('distinguishes duplicate Player names through UUID relationships and codes', () => {
    const input = createLiveSessionInput()
    const players = input.players.map((player) =>
      player.id === 'player-1' || player.id === 'player-2'
        ? { ...player, displayName: 'Nguyễn An' }
        : player,
    )

    const model = composePlayerSessionViewModel(
      { ...input, players, matchPlans: [queuedPlan()] },
      'participant-2',
    )

    expect(model?.participant).toMatchObject({
      sessionParticipantId: 'participant-2',
      participantCode: 2,
      displayName: 'Nguyễn An',
    })
    expect(model?.activity?.teammate).toMatchObject({
      sessionParticipantId: 'participant-1',
      participantCode: 1,
      displayName: 'Nguyễn An',
    })
  })

  it('never resolves a Participant by participantCode', () => {
    const input = createLiveSessionInput()

    expect(composePlayerSessionViewModel(input, '1')).toBeNull()
  })

  it('keeps incomplete relationships empty instead of inventing Players', () => {
    const input = createLiveSessionInput()
    const incompletePlan = queuedPlan([
      {
        id: 'target',
        sessionParticipantId: 'participant-1',
        teamSide: 'A',
        teamSlot: 1,
      },
      {
        id: 'known-opponent',
        sessionParticipantId: 'participant-3',
        teamSide: 'B',
        teamSlot: 1,
      },
      {
        id: 'unknown-opponent',
        sessionParticipantId: 'missing-participant',
        teamSide: 'B',
        teamSlot: 2,
      },
    ])

    const model = composePlayerSessionViewModel(
      { ...input, matchPlans: [incompletePlan] },
      'participant-1',
    )

    expect(model?.activity?.teammate).toBeNull()
    expect(model?.activity?.opponents).toEqual([
      {
        sessionParticipantId: 'participant-3',
        participantCode: 3,
        displayName: 'Chi Le',
      },
    ])
    expect(model?.detailsIncomplete).toBe(true)
  })
})
