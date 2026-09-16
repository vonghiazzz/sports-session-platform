import type { CourtView, ParticipantView } from './liveSessionModel'
import { MatchmakingRecommendationDetails } from './MatchmakingRecommendationDetails'
import { useMatchmakingRecommendation } from './useMatchmakingRecommendation'

export function MatchmakingRecommendation({
  sessionId,
  court,
  participants,
}: {
  readonly sessionId: string
  readonly court: CourtView
  readonly participants: readonly ParticipantView[]
}) {
  const action = useMatchmakingRecommendation(
    sessionId,
    court.sessionCourtId,
  )

  const participantById = new Map(
    participants.map((participant) => [
      participant.sessionParticipantId,
      participant,
    ]),
  )

  const recommendation = action.recommendation

  return (
    <section
      className="matchmaking-recommendation"
      aria-label={`Đề xuất trận cho ${court.name}`}
    >
      {recommendation === null ? (
        <button
          className="primary-action-button"
          type="button"
          disabled={action.isGenerating}
          onClick={() => void action.generate()}
        >
          {action.isGenerating
            ? 'Đang tạo đề xuất…'
            : 'Tạo đề xuất ghép trận'}
        </button>
      ) : (
        <div className="recommendation-card">
          <div className="recommendation-heading">
            <div>
              <p className="eyebrow">Đề xuất trận</p>
              <h4>{court.name}</h4>
            </div>

            <span>
              {recommendation.eligiblePlayerCount} người đủ điều kiện
            </span>
          </div>

          <MatchmakingRecommendationDetails
            recommendation={recommendation}
            participantById={participantById}
          />

          <div className="recommendation-actions">
            <button
              className="primary-action-button"
              type="button"
              disabled={action.isQueueing || action.queueBlocked}
              onClick={() => void action.addToQueue()}
            >
              {action.isQueueing
                ? 'Đang đưa vào hàng chờ…'
                : 'Đưa vào hàng chờ'}
            </button>

            <button
              className="secondary-action-button"
              type="button"
              disabled={
                action.isGenerating ||
                action.isQueueing ||
                action.queueBlocked
              }
              onClick={action.dismiss}
            >
              Bỏ đề xuất
            </button>

            {action.queueBlocked && (
              <button
                className="secondary-action-button"
                type="button"
                disabled={action.isCheckingQueue}
                onClick={() => void action.checkQueueOutcome()}
              >
                {action.isCheckingQueue
                  ? 'Đang kiểm tra…'
                  : 'Kiểm tra lại'}
              </button>
            )}

          </div>
        </div>
      )}

      {action.generateError && (
        <p className="action-feedback" role="alert">
          {action.generateError}
        </p>
      )}

      {action.queueError && (
        <p className="action-feedback" role="alert">
          {action.queueError}
        </p>
      )}

      <p className="recommendation-note">
        Đề xuất được tính từ trạng thái hiện tại. Nếu dữ liệu chưa thay đổi, kết
        quả có thể giống trước. Đề xuất chưa giữ sân hoặc người chơi; hãy đưa
        vào hàng chờ rồi bắt đầu từ hàng chờ của sân khi sẵn sàng.
      </p>

      <p className="recommendation-fallback">
        Không phù hợp? Bạn có thể tự chọn bốn người bằng “Xếp vào hàng chờ”.
      </p>
    </section>
  )
}
