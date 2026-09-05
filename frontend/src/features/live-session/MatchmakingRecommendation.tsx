import type { MatchmakingPlayerResponse } from '../../api/contracts'
import type { CourtView, ParticipantView } from './liveSessionModel'
import { useMatchmakingRecommendation } from './useMatchmakingRecommendation'

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
      <span>{participant?.skillLabel ?? 'Chưa có trình độ'}</span>
      <span>
        {participant?.waitingDuration === null ||
        participant?.waitingDuration === undefined
          ? 'Chưa có thời gian chờ'
          : `Chờ ${participant.waitingDuration}`}
      </span>
    </li>
  )
}

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
            <span>{recommendation.eligiblePlayerCount} người đủ điều kiện</span>
          </div>
          <div className="recommendation-teams">
            <div>
              <h5>Đội A</h5>
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
              <h5>Đội B</h5>
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
            Ưu tiên người chờ lâu và cân bằng hai đội. Đề xuất chưa chiếm dụng
            tài nguyên; hệ thống sẽ kiểm tra lại khi chấp nhận.
          </p>
          <div className="recommendation-actions">
            <button
              className="primary-action-button"
              type="button"
              disabled={action.isAccepting || action.acceptBlocked}
              onClick={() => void action.accept()}
            >
              {action.isAccepting
                ? 'Đang chấp nhận…'
                : 'Chấp nhận & bắt đầu'}
            </button>
            <button
              className="secondary-action-button"
              type="button"
              disabled={action.isAccepting || action.isGenerating}
              onClick={action.dismiss}
            >
              Bỏ đề xuất
            </button>
            {action.acceptBlocked && (
              <button
                className="secondary-action-button"
                type="button"
                disabled={action.isGenerating}
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
      <p className="recommendation-fallback">
        Không phù hợp? Bạn vẫn có thể tạo trận thủ công bên dưới.
      </p>
    </section>
  )
}
