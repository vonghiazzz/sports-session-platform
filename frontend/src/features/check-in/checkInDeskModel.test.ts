import { describe, expect, it } from 'vitest'
import type {
  PlayerResponse,
  SessionParticipantResponse,
} from '../../api/contracts'
import { createLiveSessionInput } from '../../test/liveSessionFixtures'
import {
  composeCheckInParticipants,
  filterCheckInParticipants,
  summarizeCheckInParticipants,
} from './checkInDeskModel'

function withIdentity(
  participant: SessionParticipantResponse,
  id: string,
  playerId: string,
  participantCode: number,
): SessionParticipantResponse {
  return { ...participant, id, playerId, participantCode }
}

function withPlayerIdentity(
  player: PlayerResponse,
  id: string,
  displayName: string,
): PlayerResponse {
  return {
    ...player,
    id,
    displayName,
    sportProfiles: player.sportProfiles.map((profile) => ({
      ...profile,
      id: `profile-${id}`,
    })),
  }
}

describe('checkInDeskModel', () => {
  it('sorts by participant code without mutating backend order', () => {
    const input = createLiveSessionInput()
    const backendOrder = [
      { ...input.participants[0], participantCode: 12 },
      { ...input.participants[1], participantCode: 3 },
      { ...input.participants[2], participantCode: 8 },
    ]

    expect(
      composeCheckInParticipants(backendOrder, input.players).map(
        (participant) => participant.participantCode,
      ),
    ).toEqual([3, 8, 12])
    expect(backendOrder.map((participant) => participant.participantCode)).toEqual([
      12, 3, 8,
    ])
  })

  it('searches exact code with or without hash and names case-insensitively', () => {
    const input = createLiveSessionInput()
    const baseParticipant = input.participants[0]
    const basePlayer = input.players[0]
    const participants = composeCheckInParticipants(
      [
        withIdentity(baseParticipant, 'participant-3', 'player-3', 3),
        withIdentity(baseParticipant, 'participant-8', 'player-8', 8),
        withIdentity(baseParticipant, 'participant-12', 'player-12', 12),
      ],
      [
        withPlayerIdentity(basePlayer, 'player-3', 'Nguyễn An'),
        withPlayerIdentity(basePlayer, 'player-8', 'Nguyễn An'),
        withPlayerIdentity(basePlayer, 'player-12', 'Bình'),
      ],
    )

    expect(filterCheckInParticipants(participants, ' 12 ')).toHaveLength(1)
    expect(filterCheckInParticipants(participants, '#12')[0]?.participantCode).toBe(
      12,
    )
    expect(
      filterCheckInParticipants(participants, 'NGUYEN an').map(
        (participant) => participant.participantCode,
      ),
    ).toEqual([3, 8])
    expect(filterCheckInParticipants(participants, '#3')[0]?.sessionParticipantId).toBe(
      'participant-3',
    )
  })

  it('derives checked-in history from checkedInAt and never assumes every LEFT participant checked in', () => {
    const input = createLiveSessionInput()
    const registered = input.participants[2]
    const checkedInLeft = {
      ...registered,
      id: 'left-after-check-in',
      status: 'LEFT' as const,
      checkedInAt: '2026-09-02T09:15:00Z',
      leftAt: '2026-09-02T10:00:00Z',
    }
    const neverCheckedInLeft = {
      ...checkedInLeft,
      id: 'left-before-check-in',
      checkedInAt: null,
    }
    const participants = composeCheckInParticipants(
      [registered, checkedInLeft, neverCheckedInLeft],
      input.players,
    )

    expect(summarizeCheckInParticipants(participants)).toEqual({
      total: 3,
      registered: 1,
      checkedIn: 1,
    })
  })
})
