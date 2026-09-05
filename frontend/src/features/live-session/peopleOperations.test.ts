import { describe, expect, it } from 'vitest'
import type { ParticipantView } from './liveSessionModel'
import {
  filterParticipantsByName,
  normalizePlayerSearch,
} from './peopleOperations'

function participant(displayName: string): ParticipantView {
  return {
    sessionParticipantId: displayName,
    displayName,
    status: 'WAITING',
    skillLevel: 'INTERMEDIATE',
    skillLabel: 'TB',
    waitingSince: '2026-09-02T09:00:00Z',
    waitingDuration: '1 giờ',
    dataUnavailable: false,
  }
}

describe('People search', () => {
  const participants = [
    participant('Nguyễn Nghĩa'),
    participant('Trần Bình'),
    participant('LÊ AN'),
  ]

  it('matches Player display names case-insensitively', () => {
    expect(filterParticipantsByName(participants, 'lê an')).toEqual([
      participants[2],
    ])
  })

  it('matches Vietnamese display names without requiring diacritics', () => {
    expect(filterParticipantsByName(participants, 'nghia')).toEqual([
      participants[0],
    ])
    expect(normalizePlayerSearch('Đặng')).toBe('dang')
  })

  it('returns the original group when search is blank', () => {
    expect(filterParticipantsByName(participants, '   ')).toBe(participants)
  })
})
