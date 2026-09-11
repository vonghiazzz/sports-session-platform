import type { MatchmakingUnavailableReason } from '../../api/contracts'
import type { CourtView, ParticipantView } from './liveSessionModel'
import { MatchmakingRecommendationDetails } from './MatchmakingRecommendationDetails'
import { useGlobalMatchmakingRecommendation } from './useGlobalMatchmakingRecommendation'

function unavailableMessage(reason: MatchmakingUnavailableReason): string {
  switch (reason) {
    case 'INSUFFICIENT_ELIGIBLE_PLAYERS':
      return 'Không còn đủ bốn người chơi phù hợp để tạo đề xuất cho sân này.'
  }
}

export function GlobalMatchmakingRecommendation({
  sessionId,
  courts,
  participants,
}: {
  readonly sessionId: string
  readonly courts: readonly CourtView[]
  readonly participants: readonly ParticipantView[]
}) {
  const action = useGlobalMatchmakingRecommendation(sessionId)
  const courtById = new Map(
    courts.map((court) => [court.sessionCourtId, court]),
  )
  const participantById = new Map(
    participants.map((participant) => [
      participant.sessionParticipantId,
      participant,
    ]),
  )

  const preview = action.preview

  return (
    <section
      className={`global-matchmaking-preview${action.isStale ? ' global-matchmaking-stale' : ''}`}
      aria-labelledby="global-matchmaking-heading"
    >
      <div className="global-matchmaking-heading">
        <div>
          <p className="eyebrow">Điều phối nhiều sân</p>
          <h3 id="global-matchmaking-heading">Đề xuất toàn phiên</h3>
        </div>

        {preview !== null && (
          <span>{preview.initialEligiblePlayerCount} người đủ điều kiện ban đầu</span>
        )}
      </div>

      {preview === null ? (
        <button
          className="primary-action-button"
          type="button"
          disabled={action.isGenerating}
          onClick={() => void action.generate()}
        >
          {action.isGenerating
            ? 'Đang tạo đề xuất toàn phiên…'
            : 'Tạo đề xuất cho các sân sẵn sàng'}
        </button>
      ) : (
        <>
          {preview.outcome === 'UNAVAILABLE' &&
          preview.reason === 'NO_ELIGIBLE_COURTS' ? (
            <p className="global-matchmaking-empty" role="status">
              Hiện không có sân sẵn sàng để tạo đề xuất.
            </p>
          ) : (
            <div className="global-matchmaking-results">
              {preview.courtResults.map((result) => {
                const courtName =
                  courtById.get(result.sessionCourtId)?.name ??
                  'Không có dữ liệu sân'

                return result.outcome === 'RECOMMENDED' ? (
                  <article
                    className="recommendation-card global-recommendation-card"
                    key={result.sessionCourtId}
                    aria-label={`Đề xuất toàn phiên cho ${courtName}`}
                  >
                    <div className="recommendation-heading">
                      <div>
                        <p className="eyebrow">Đề xuất trận</p>
                        <h4>{courtName}</h4>
                      </div>

                      <span>{result.eligiblePlayerCount} người còn phù hợp</span>
                    </div>

                    <MatchmakingRecommendationDetails
                      recommendation={result}
                      participantById={participantById}
                    />
                  </article>
                ) : (
                  <article
                    className="global-recommendation-unavailable"
                    key={result.sessionCourtId}
                    aria-label={`Không có đề xuất toàn phiên cho ${courtName}`}
                  >
                    <div>
                      <p className="eyebrow">Chưa có đề xuất</p>
                      <h4>{courtName}</h4>
                    </div>
                    <p>{unavailableMessage(result.reason)}</p>
                    <span>
                      Còn {result.eligiblePlayerCount} người đủ điều kiện
                    </span>
                  </article>
                )
              })}
            </div>
          )}

          <div className="recommendation-actions">
            {(action.canQueue || action.isQueueing) && (
              <button
                className="primary-action-button"
                type="button"
                disabled={!action.canQueue}
                onClick={() => void action.queueAll()}
              >
                {action.isQueueing
                  ? 'Đang thêm vào hàng chờ…'
                  : 'Thêm tất cả vào hàng chờ'}
              </button>
            )}

            <button
              className="secondary-action-button"
              type="button"
              disabled={!action.canRegenerate}
              onClick={() => void action.generate()}
            >
              {action.isGenerating ? 'Đang tạo lại…' : 'Tạo lại'}
            </button>

            <button
              className="secondary-action-button"
              type="button"
              disabled={!action.canDismiss}
              onClick={action.dismiss}
            >
              Bỏ đề xuất
            </button>
          </div>

          {action.isReconciling && (
            <p className="recommendation-note" role="status">
              Đang kiểm tra trạng thái hàng chờ…
            </p>
          )}

          {action.hasUnknownOutcome && (
            <button
              className="secondary-action-button"
              type="button"
              onClick={() => void action.checkQueueOutcome()}
            >
              Kiểm tra lại
            </button>
          )}
        </>
      )}

      {action.generateError && (
        <p className="action-feedback" role="alert">
          {action.generateError}
        </p>
      )}

      {action.queueMessage && (
        <p className="action-feedback" role="alert">
          {action.queueMessage}
        </p>
      )}

      {action.successMessage && (
        <p className="global-matchmaking-success" role="status">
          {action.successMessage}
        </p>
      )}

      <p className="recommendation-note">
        Bản xem trước chỉ đọc, không giữ sân hoặc người chơi và không tạo trận.
      </p>
    </section>
  )
}
