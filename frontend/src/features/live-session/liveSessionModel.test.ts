import { describe, expect, it } from 'vitest'
import { createLiveSessionInput } from '../../test/liveSessionFixtures'
import type { MatchResponse, ParticipantStatus } from '../../api/contracts'
import {
  composeLiveSessionModel,
  formatWaitingDuration,
} from './liveSessionModel'

describe('composeLiveSessionModel', () => {
  it('enriches a Session Participant with the Player display name', () => {
    const model = composeLiveSessionModel(createLiveSessionInput())

    expect(model.waitingParticipants[0]?.displayName).toBe('An Nguyen')
  })

  it('maps backend SkillLevel to the approved presentation label', () => {
    const model = composeLiveSessionModel(createLiveSessionInput())

    expect(model.waitingParticipants[0]).toMatchObject({
      skillLevel: 'WEAK',
      skillLabel: 'Yếu',
    })

    expect(model.waitingParticipants[1]).toMatchObject({
      skillLevel: 'WEAK_PLUS',
      skillLabel: 'Yếu+',
    })
  })

  it('counts distinct COMPLETED Session Matches by SessionParticipant UUID only', () => {
    const input = createLiveSessionInput()
    const created = input.matches.find((match) => match.status === 'CREATED')
    const playing = input.matches.find((match) => match.status === 'PLAYING')
    expect(created).toBeDefined()
    expect(playing).toBeDefined()
    if (created === undefined || playing === undefined) {
      throw new Error('Expected CREATED and PLAYING Match fixtures')
    }

    const completedOne: MatchResponse = {
      ...created,
      id: 'match-completed-1',
      status: 'COMPLETED',
      startedAt: '2026-09-02T09:40:00Z',
      completedAt: '2026-09-02T09:50:00Z',
    }
    const completedTwo: MatchResponse = {
      ...playing,
      id: 'match-completed-2',
      status: 'COMPLETED',
      participants: playing.participants.map((assignment, index) =>
        index === 0
          ? { ...assignment, sessionParticipantId: 'participant-1' }
          : assignment,
      ),
      completedAt: '2026-09-02T09:55:00Z',
    }
    const cancelled: MatchResponse = {
      ...created,
      id: 'match-cancelled',
      status: 'CANCELLED',
      cancelledAt: '2026-09-02T09:55:00Z',
    }
    const otherSession: MatchResponse = {
      ...completedOne,
      id: 'match-other-session',
      sessionId: 'session-2',
    }
    const queuedPlan = {
      id: 'plan-queued',
      sessionId: input.session.id,
      sessionCourtId: 'session-court-2',
      source: 'MANUAL' as const,
      status: 'QUEUED' as const,
      queuePosition: 1,
      startedMatchId: null,
      participants: [
        {
          id: 'plan-participant-1',
          sessionParticipantId: 'participant-1',
          teamSide: 'A' as const,
          teamSlot: 1,
        },
      ],
      createdAt: '2026-09-02T09:56:00Z',
      startedAt: null,
      cancelledAt: null,
      updatedAt: '2026-09-02T09:56:00Z',
      version: 0,
    }
    const model = composeLiveSessionModel({
      ...input,
      players: input.players.map((player) =>
        player.id === 'player-1' || player.id === 'player-2'
          ? { ...player, displayName: 'Trùng Tên' }
          : player,
      ),
      matches: [
        created,
        playing,
        cancelled,
        completedOne,
        completedOne,
        completedTwo,
        otherSession,
      ],
      matchPlans: [queuedPlan],
    })
    const allParticipants = [
      ...model.waitingParticipants,
      ...model.playingParticipants,
      ...model.registeredParticipants,
      ...model.pausedParticipants,
      ...model.leftParticipants,
    ]
    const countFor = (sessionParticipantId: string) =>
      allParticipants.find(
        (participant) =>
          participant.sessionParticipantId === sessionParticipantId,
      )?.completedMatchCount

    expect(countFor('participant-1')).toBe(2)
    expect(countFor('participant-2')).toBe(1)
    expect(countFor('participant-3')).toBe(1)
    expect(countFor('participant-8')).toBe(0)
  })

  it('updates the count from authoritative Match data and starts runtime additions at zero', () => {
    const input = createLiveSessionInput()
    const created = input.matches.find((match) => match.status === 'CREATED')
    expect(created).toBeDefined()
    if (created === undefined) {
      throw new Error('Expected CREATED Match fixture')
    }
    const addedPlayer = {
      ...input.players[0],
      id: 'player-9',
      playerCode: 'P000009',
      displayName: 'Người mới',
    }
    const addedParticipant = {
      ...input.participants[0],
      id: 'participant-9',
      playerId: addedPlayer.id,
      participantCode: 9,
    }
    const before = composeLiveSessionModel({
      ...input,
      players: [...input.players, addedPlayer],
      participants: [...input.participants, addedParticipant],
    })
    const after = composeLiveSessionModel({
      ...input,
      players: [...input.players, addedPlayer],
      participants: [...input.participants, addedParticipant],
      matches: [
        ...input.matches.filter((match) => match.id !== created.id),
        {
          ...created,
          status: 'COMPLETED',
          startedAt: '2026-09-02T09:40:00Z',
          completedAt: '2026-09-02T09:50:00Z',
        },
      ],
    })
    const waitingCount = (model: typeof before, id: string) =>
      model.waitingParticipants.find(
        (participant) => participant.sessionParticipantId === id,
      )?.completedMatchCount

    expect(waitingCount(before, 'participant-1')).toBe(0)
    expect(waitingCount(after, 'participant-1')).toBe(1)
    expect(waitingCount(after, 'participant-9')).toBe(0)
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
    ).toBe('30 phút')
  })

  it('formats a long WAITING duration as elapsed hours and minutes', () => {
    expect(
      formatWaitingDuration(
        '2026-09-02T08:48:00Z',
        new Date('2026-09-02T10:00:00Z'),
      ),
    ).toBe('1 giờ 12 phút')
  })

  it('orders WAITING Participants by oldest waitingSince first', () => {
    const input = createLiveSessionInput()
    const model = composeLiveSessionModel({
      ...input,
      participants: input.participants.map((participant) =>
        participant.status === 'WAITING'
          ? {
              ...participant,
              waitingSince:
                participant.playerId === 'player-1'
                  ? '2026-09-02T09:55:00Z'
                  : '2026-09-02T09:15:00Z',
            }
          : participant,
      ),
    })

    expect(model.waitingParticipants.map((participant) => participant.displayName)).toEqual([
      'Bao Tran',
      'An Nguyen',
    ])
  })

  it('groups a 25-Participant Session exactly once with correct counts', () => {
    const input = createLiveSessionInput()
    const playerTemplate = input.players[0]
    const participantTemplate = input.participants[0]
    const statuses: ParticipantStatus[] = [
      ...Array<ParticipantStatus>(8).fill('WAITING'),
      ...Array<ParticipantStatus>(8).fill('PLAYING'),
      ...Array<ParticipantStatus>(4).fill('REGISTERED'),
      ...Array<ParticipantStatus>(3).fill('PAUSED'),
      ...Array<ParticipantStatus>(2).fill('LEFT'),
    ]
    const players = statuses.map((_, index) => ({
      ...playerTemplate,
      id: `scale-player-${index}`,
      displayName: `Người chơi ${index + 1}`,
    }))
    const participants = statuses.map((status, index) => ({
      ...participantTemplate,
      id: `scale-participant-${index}`,
      playerId: `scale-player-${index}`,
      status,
      waitingSince:
        status === 'WAITING'
          ? `2026-09-02T09:${String(index).padStart(2, '0')}:00Z`
          : null,
      pausedAt: status === 'PAUSED' ? '2026-09-02T09:30:00Z' : null,
      leftAt: status === 'LEFT' ? '2026-09-02T09:30:00Z' : null,
    }))

    const model = composeLiveSessionModel({
      ...input,
      players,
      participants,
      matches: [],
    })
    const groupedIds = [
      ...model.waitingParticipants,
      ...model.playingParticipants,
      ...model.registeredParticipants,
      ...model.pausedParticipants,
      ...model.leftParticipants,
    ].map((participant) => participant.sessionParticipantId)

    expect(model.participantCount).toBe(25)
    expect([
      model.waitingParticipants.length,
      model.playingParticipants.length,
      model.registeredParticipants.length,
      model.pausedParticipants.length,
      model.leftParticipants.length,
    ]).toEqual([8, 8, 4, 3, 2])
    expect(new Set(groupedIds).size).toBe(25)
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
      'Không có dữ liệu người chơi',
    )
    expect(model.warnings).toContain(
      'Không thể xác định dữ liệu của một số người chơi.',
    )
  })

  it('keeps a Session Court visible when physical Court enrichment is missing', () => {
    const input = createLiveSessionInput()
    const model = composeLiveSessionModel({
      ...input,
      venueCourts: input.venueCourts.filter((court) => court.id !== 'court-1'),
    })

    expect(model.courts).toHaveLength(3)
    expect(model.courts[0]?.name).toBe('Không có dữ liệu sân')
    expect(model.warnings).toContain(
      'Không thể xác định dữ liệu của một số sân.',
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
      'Một sân đang chơi không có trận đấu tương ứng.',
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
      'Không có dữ liệu người chơi',
    )
    expect(model.warnings).toContain('Phân công đội của một trận đấu chưa đầy đủ.')
  })

  it('derives a compact nearest-plan label for People operations', () => {
    const input = createLiveSessionInput()
    const matchPlan = {
      id: 'plan-1',
      sessionId: input.session.id,
      sessionCourtId: 'session-court-2',
      source: 'MANUAL' as const,
      status: 'QUEUED' as const,
      queuePosition: 1,
      startedMatchId: null,
      participants: [
        { id: 'plan-player-1', sessionParticipantId: 'participant-1', teamSide: 'A' as const, teamSlot: 1 },
      ],
      createdAt: '2026-09-02T10:00:00Z',
      startedAt: null,
      cancelledAt: null,
      updatedAt: '2026-09-02T10:00:00Z',
      version: 0,
    }
    const model = composeLiveSessionModel({ ...input, matchPlans: [matchPlan] })

    expect(model.waitingParticipants[0]?.planningLabel).toBe(
      'Sắp chơi • Court Two • lượt 1',
    )
  })

  it('aggregates multiple active plans without including cancelled history', () => {
    const input = createLiveSessionInput()
    const base = {
      id: 'plan-1',
      sessionId: input.session.id,
      sessionCourtId: 'session-court-2',
      source: 'MANUAL' as const,
      status: 'QUEUED' as const,
      queuePosition: 1,
      startedMatchId: null,
      participants: [
        { id: 'plan-player-1', sessionParticipantId: 'participant-1', teamSide: 'A' as const, teamSlot: 1 },
      ],
      createdAt: '2026-09-02T10:00:00Z',
      startedAt: null,
      cancelledAt: null,
      updatedAt: '2026-09-02T10:00:00Z',
      version: 0,
    }
    const model = composeLiveSessionModel({
      ...input,
      matchPlans: [
        base,
        { ...base, id: 'plan-2', queuePosition: 2 },
        { ...base, id: 'plan-3', status: 'CANCELLED', queuePosition: null },
      ],
    })

    expect(model.waitingParticipants[0]?.plannedMatchCount).toBe(2)
    expect(model.waitingParticipants[0]?.planningLabel).toBe(
      'Đã xếp 2 trận sắp tới',
    )
  })
})
