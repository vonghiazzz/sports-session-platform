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
            : action.generateError
              ? 'Tạo đề xuất mới'
              : 'Tạo đề xuất'}
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
              disabled={
                court.status !== 'AVAILABLE' ||
                action.isAccepting ||
                action.isQueueing ||
                action.acceptBlocked ||
                action.queueBlocked
              }
              onClick={() => void action.accept()}
            >
              {action.isAccepting
                ? 'Đang chấp nhận…'
                : 'Chấp nhận & bắt đầu'}
            </button>

            <button
              className="secondary-action-button"
              type="button"
              disabled={
                action.isQueueing ||
                action.isAccepting ||
                action.queueBlocked
              }
              onClick={() => void action.addToQueue()}
            >
              {action.isQueueing
                ? 'Đang thêm vào hàng chờ…'
                : 'Thêm vào hàng chờ'}
            </button>

            <button
              className="secondary-action-button"
              type="button"
              disabled={
                action.isAccepting ||
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

            {action.acceptBlocked && !action.queueBlocked && (
              <button
                className="secondary-action-button"
                type="button"
                disabled={action.isGenerating || action.isQueueing}
                onClick={() => void action.generate()}
              >
                {action.isGenerating
                  ? 'Đang tạo đề xuất…'
                  : 'Tạo đề xuất mới'}
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

      {action.acceptError && (
        <p className="action-feedback" role="alert">
          {action.acceptError}
        </p>
      )}

      {action.queueError && (
        <p className="action-feedback" role="alert">
          {action.queueError}
        </p>
      )}

      <p className="recommendation-note">
        Ưu tiên người chờ lâu và cân bằng hai đội. Đề xuất chưa giữ sân hoặc
        người chơi; bạn có thể bắt đầu ngay khi đủ điều kiện hoặc thêm vào hàng
        chờ để chuẩn bị trước.
      </p>

      <p className="recommendation-fallback">
        Không phù hợp? Bạn vẫn có thể tạo trận thủ công bên dưới.
      </p>
    </section>
  )
}
