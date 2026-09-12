import type {
  PlayerRatingBasis,
  PlayerSportProfileResponse,
  SkillLevel,
} from '../../api/contracts'

export const PLAYER_SKILL_LEVELS: readonly SkillLevel[] = [
  'WEAK',
  'WEAK_PLUS',
  'INTERMEDIATE_MINUS',
  'INTERMEDIATE',
  'INTERMEDIATE_PLUS',
  'GOOD',
]

const ratingFormatter = new Intl.NumberFormat('vi-VN', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 2,
})

export function normalizePlayerSearch(value: string): string {
  return value.trim()
}

export function formatPlayerRating(value: number): string {
  return ratingFormatter.format(value)
}

export function ratedMatchesLabel(ratedMatches: number): string {
  if (ratedMatches === 0) {
    return 'Chưa có trận được tính Rating'
  }
  if (ratedMatches === 1) {
    return '1 trận đã tính Rating'
  }
  return `${ratedMatches} trận đã tính Rating`
}

export function ratingBasisLabel(basis: PlayerRatingBasis): string {
  return basis === 'INITIAL_PRIOR'
    ? 'Điểm khởi tạo theo trình Host đánh giá'
    : 'Rating đã học từ kết quả thi đấu'
}

export function badmintonProfile(
  profiles: readonly PlayerSportProfileResponse[],
): PlayerSportProfileResponse | undefined {
  return profiles.find((profile) => profile.sport === 'BADMINTON')
}
