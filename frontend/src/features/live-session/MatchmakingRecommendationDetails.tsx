import type {
  MatchmakingPlayerResponse,
  MatchRecommendationResponse,
} from '../../api/contracts'
import type { ParticipantView } from './liveSessionModel'

const ratingFormatter = new Intl.NumberFormat('vi-VN', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 2,
})

function formatRating(value: number) {
  return ratingFormatter.format(value)
}

function ratingContext(player: MatchmakingPlayerResponse) {
  if (player.ratingBasis === 'INITIAL_PRIOR') {
    return 'Điểm khởi tạo'
  }

  return `rating từ ${player.ratedMatches} trận`
}

function RecommendationPlayer({
  player,
  participantById,
}: {
  readonly player: MatchmakingPlayerResponse
  readonly participantById: ReadonlyMap<string, ParticipantView>
}) {
  const participant = participantById.get(player.sessionParticipantId)

  return (
    <li>
      <strong>
        {participant?.displayName ?? 'Không có dữ liệu người chơi'}
      </strong>

      <span>
        Trình độ: {participant?.skillLabel ?? 'Chưa có trình độ'}
      </span>

      <span>
        Trong phiên: {player.sessionMatchesPlayed} trận hoàn tất
      </span>

      <span>
        Rating: {formatRating(player.ratingValue)} · {ratingContext(player)}
      </span>

      <span>
        {participant?.waitingDuration === null ||
        participant?.waitingDuration === undefined
          ? 'Chưa có thời gian chờ'
          : `Chờ ${participant.waitingDuration}`}
      </span>
    </li>
  )
}

export function MatchmakingRecommendationDetails({
  recommendation,
  participantById,
}: {
  readonly recommendation: MatchRecommendationResponse
  readonly participantById: ReadonlyMap<string, ParticipantView>
}) {
  return (
    <>
      <div className="recommendation-teams">
        <div>
          <h5>
            Đội A · Tổng Rating{' '}
            {formatRating(recommendation.teamARatingTotal)}
          </h5>

          <ul>
            <RecommendationPlayer
              player={recommendation.teamA.slot1}
              participantById={participantById}
            />

            <RecommendationPlayer
              player={recommendation.teamA.slot2}
              participantById={participantById}
            />
          </ul>
        </div>

        <div>
          <h5>
            Đội B · Tổng Rating{' '}
            {formatRating(recommendation.teamBRatingTotal)}
          </h5>

          <ul>
            <RecommendationPlayer
              player={recommendation.teamB.slot1}
              participantById={participantById}
            />

            <RecommendationPlayer
              player={recommendation.teamB.slot2}
              participantById={participantById}
            />
          </ul>
        </div>
      </div>

      <p className="recommendation-note">
        Chênh lệch Rating giữa hai đội:{' '}
        <strong>{formatRating(recommendation.ratingDifference)}</strong>
      </p>

      <p className="recommendation-note">
        Matchmaking cân đội bằng Rating hiện tại. Trình độ Yếu, TB, Khá là
        thông tin hồ sơ ban đầu và có thể khác Rating sau khi người chơi đã có
        lịch sử thi đấu.
      </p>
    </>
  )
}
