import { describe, expect, it } from 'vitest'
import { createLiveSessionInput } from '../../test/liveSessionFixtures'
import {
  composeLiveSessionModel,
  formatWaitingDuration,
  SKILL_LEVEL_LABELS,
} from './liveSessionModel'

describe('composeLiveSessionModel', () => {
  it('enriches a Session Participant with the Player display name', () => {
    const model = composeLiveSessionModel(createLiveSessionInput())

    expect(model.waitingParticipants[0]?.displayName).toBe('An Nguyen')
  })

  it('maps every backend SkillLevel to the approved presentation label', () => {
    expect(SKILL_LEVEL_LABELS).toEqual({
      WEAK: 'Yếu',
      WEAK_PLUS: 'Yếu+',
      INTERMEDIATE_MINUS: 'TB-',
      INTERMEDIATE: 'TB',
      INTERMEDIATE_PLUS: 'TB+',
      GOOD: 'Khá',
    })
  })

  it('enriches a Session Court with its physical name and runtime status', () => {
    const model = composeLiveSessionModel(createLiveSessionInput())

    expect(model.courts[0]).toMatchObject({
      name: 'Court One',
      status: 'PLAYING',
    })
  })

  it('calculates WAITING duration from waitingSince to current time', () => {
    expect(
      formatWaitingDuration(
        '2026-09-02T09:30:00Z',
        new Date('2026-09-02T10:00:00Z'),
      ),
    ).toBe('30 min')
  })

  it('resolves PLAYING Match A1, A2, B1, and B2 by semantic assignment', () => {
    const model = composeLiveSessionModel(createLiveSessionInput())
    const match = model.courts[0]?.activeMatch

    expect(match?.teamA.map((member) => member.displayName)).toEqual([
      'Giang Vo',
      'Hanh Bui',
    ])
    expect(match?.teamB.map((member) => member.displayName)).toEqual([
      'Khanh Do',
      'Linh Ho',
    ])
  })

  it('represents CREATED Matches separately from PLAYING Matches', () => {
    const model = composeLiveSessionModel(createLiveSessionInput())

    expect(model.createdMatches).toHaveLength(1)
    expect(model.createdMatches[0]).toMatchObject({
      status: 'CREATED',
      courtName: 'Court Two',
    })
    expect('reserved' in (model.createdMatches[0] ?? {})).toBe(false)
  })

  it('keeps a Participant visible when Player enrichment is missing', () => {
    const input = createLiveSessionInput()
    const model = composeLiveSessionModel({
      ...input,
      players: input.players.filter((player) => player.id !== 'player-1'),
    })

    expect(model.waitingParticipants).toHaveLength(2)
    expect(model.waitingParticipants[0]?.displayName).toBe(
      'Player data unavailable',
    )
    expect(model.warnings).toContain(
      'Some Participant player data could not be resolved.',
    )
  })

  it('keeps a Session Court visible when physical Court enrichment is missing', () => {
    const input = createLiveSessionInput()
    const model = composeLiveSessionModel({
      ...input,
      venueCourts: input.venueCourts.filter((court) => court.id !== 'court-1'),
    })

    expect(model.courts).toHaveLength(3)
    expect(model.courts[0]?.name).toBe('Court data unavailable')
    expect(model.warnings).toContain(
      'Some physical Court data could not be resolved.',
    )
  })

  it('warns when a PLAYING Court has no resolvable PLAYING Match', () => {
    const input = createLiveSessionInput()
    const model = composeLiveSessionModel({
      ...input,
      matches: input.matches.filter((match) => match.status !== 'PLAYING'),
    })

    expect(model.courts[0]?.activeMatch).toBeNull()
    expect(model.warnings).toContain(
      'A PLAYING Court has no resolvable PLAYING Match.',
    )
  })

  it('keeps an unavailable placeholder when a Match team slot cannot resolve', () => {
    const input = createLiveSessionInput()
    const playingMatch = input.matches.find((match) => match.status === 'PLAYING')
    const model = composeLiveSessionModel({
      ...input,
      matches: playingMatch
        ? [
            {
              ...playingMatch,
              participants: playingMatch.participants.filter(
                (participant) =>
                  !(participant.teamSide === 'B' && participant.teamSlot === 2),
              ),
            },
          ]
        : [],
    })

    expect(model.courts[0]?.activeMatch?.teamB[1]?.displayName).toBe(
      'Player data unavailable',
    )
    expect(model.warnings).toContain('A Match team assignment is incomplete.')
  })
})
