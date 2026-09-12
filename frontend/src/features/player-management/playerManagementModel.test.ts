import { describe, expect, it } from 'vitest'
import {
  badmintonProfile,
  formatPlayerRating,
  formatPlayerRatingDelta,
  normalizePlayerSearch,
  PLAYER_SKILL_LEVELS,
  ratedMatchesLabel,
  ratingBasisLabel,
  ratingOutcomeLabel,
} from './playerManagementModel'

describe('Player management presentation model', () => {
  it('contains exactly the six Host-controlled SkillLevel choices', () => {
    expect(PLAYER_SKILL_LEVELS).toEqual([
      'WEAK',
      'WEAK_PLUS',
      'INTERMEDIATE_MINUS',
      'INTERMEDIATE',
      'INTERMEDIATE_PLUS',
      'GOOD',
    ])
  })

  it('normalizes only surrounding search whitespace', () => {
    expect(normalizePlayerSearch('  Nguyễn An  ')).toBe('Nguyễn An')
  })

  it('formats Rating without exposing storage precision', () => {
    expect(formatPlayerRating(28.765432109)).toBe('28,77')
  })

  it('maps Rating history outcomes to Vietnamese', () => {
    expect(ratingOutcomeLabel('WIN')).toBe('Thắng')
    expect(ratingOutcomeLabel('LOSS')).toBe('Thua')
  })

  it('derives and formats positive, negative, and zero Rating changes', () => {
    expect(formatPlayerRatingDelta(27, 28.24)).toBe('+1,24')
    expect(formatPlayerRatingDelta(28.24, 27.38)).toBe('-0,86')
    expect(formatPlayerRatingDelta(27, 27)).toBe('0,0')
  })

  it('presents zero, one, and many rated matches clearly', () => {
    expect(ratedMatchesLabel(0)).toBe('Chưa có trận được tính Rating')
    expect(ratedMatchesLabel(1)).toBe('1 trận đã tính Rating')
    expect(ratedMatchesLabel(14)).toBe('14 trận đã tính Rating')
  })

  it('distinguishes initial and learned Rating bases', () => {
    expect(ratingBasisLabel('INITIAL_PRIOR')).toContain('Host đánh giá')
    expect(ratingBasisLabel('PERSISTED')).toContain('kết quả thi đấu')
  })

  it('selects only the BADMINTON profile', () => {
    const profile = {
      id: 'profile-1',
      sport: 'BADMINTON' as const,
      skillLevel: 'INTERMEDIATE' as const,
      rating: {
        ratingValue: 27,
        uncertainty: 8.333333333,
        ratedMatches: 0,
        ratingBasis: 'INITIAL_PRIOR' as const,
        ratingAlgorithmVersion: null,
      },
      createdAt: '2026-09-01T00:00:00Z',
      updatedAt: '2026-09-01T00:00:00Z',
    }
    expect(badmintonProfile([profile])).toBe(profile)
    expect(badmintonProfile([])).toBeUndefined()
  })
})
