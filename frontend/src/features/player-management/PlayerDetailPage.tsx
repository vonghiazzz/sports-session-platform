import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import type { SkillLevel } from '../../api/contracts'
import { HttpError } from '../../api/http'
import {
  formatVietnamDateTime,
  skillLevelLabel,
  sportLabel,
} from '../../lib/presentation'
import {
  badmintonProfile,
  formatPlayerRating,
  formatPlayerRatingDelta,
  PLAYER_SKILL_LEVELS,
  ratedMatchesLabel,
  ratingBasisLabel,
  ratingOutcomeLabel,
} from './playerManagementModel'
import {
  usePlayerDetail,
  usePlayerRatingHistory,
  usePlayerSkillLevelUpdate,
} from './usePlayerManagement'
import './PlayerManagement.css'

export function PlayerDetailPage() {
  const { playerId = '' } = useParams()
  const playerQuery = usePlayerDetail(playerId)
  const skillUpdate = usePlayerSkillLevelUpdate(playerId)
  const profile = playerQuery.data === undefined
    ? undefined
    : badmintonProfile(playerQuery.data.sportProfiles)
  const ratingHistoryQuery = usePlayerRatingHistory(
    playerId,
    'BADMINTON',
    'DOUBLES',
    playerQuery.isSuccess && profile !== undefined,
  )
  const [skillEdit, setSkillEdit] = useState<{
    readonly baseSkill: SkillLevel
    readonly selectedSkill: SkillLevel
  } | null>(null)
  const selectedSkill = profile === undefined
    ? ''
    : skillEdit?.baseSkill === profile.skillLevel
      ? skillEdit.selectedSkill
      : profile.skillLevel

  function submitSkill(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (
      profile === undefined ||
      selectedSkill === '' ||
      selectedSkill === profile.skillLevel ||
      skillUpdate.isPending ||
      skillUpdate.outcomeUnknown
    ) {
      return
    }
    void skillUpdate.update(profile.sport, selectedSkill)
  }

  if (playerId.length === 0) {
    return <PlayerDetailFailure message="Mã người chơi không hợp lệ." />
  }

  if (playerQuery.isPending) {
    return (
      <main className="player-management">
        <p className="player-state">Đang tải thông tin người chơi...</p>
      </main>
    )
  }

  if (playerQuery.isError) {
    const notFound = playerQuery.error instanceof HttpError
      && playerQuery.error.status === 404
    return (
      <PlayerDetailFailure
        message={notFound
          ? 'Không tìm thấy người chơi.'
          : 'Không thể tải thông tin người chơi.'}
        retry={notFound ? undefined : () => void playerQuery.refetch()}
      />
    )
  }

  const player = playerQuery.data

  return (
    <main className="player-management">
      <header className="player-management-header">
        <div>
          <p className="eyebrow">Hồ sơ người chơi</p>
          <h1>{player.displayName}</h1>
          <p>Trình độ do Host quản lý; Rating thay đổi độc lập theo kết quả thi đấu.</p>
        </div>
        <Link to="/players">Về danh sách</Link>
      </header>

      {profile === undefined ? (
        <section className="player-panel">
          <p className="player-state">Người chơi chưa có hồ sơ Cầu lông.</p>
        </section>
      ) : (
        <div className="player-detail-grid">
          <section className="player-panel" aria-labelledby="profile-title">
            <h2 id="profile-title">Trình độ Host đánh giá</h2>
            <dl className="player-details">
              <div><dt>Môn</dt><dd>{sportLabel(profile.sport)}</dd></div>
              <div><dt>Trình hiện tại</dt><dd>{skillLevelLabel(profile.skillLevel)}</dd></div>
            </dl>

            <form className="skill-form" onSubmit={submitSkill}>
              <label htmlFor="skill-level">Thay đổi trình độ</label>
              <select
                id="skill-level"
                value={selectedSkill}
                disabled={skillUpdate.isPending || skillUpdate.outcomeUnknown}
                onChange={(event) => {
                  setSkillEdit({
                    baseSkill: profile.skillLevel,
                    selectedSkill: event.target.value as SkillLevel,
                  })
                  skillUpdate.clearFeedback()
                }}
              >
                {PLAYER_SKILL_LEVELS.map((level) => (
                  <option key={level} value={level}>{skillLevelLabel(level)}</option>
                ))}
              </select>
              <button
                disabled={
                  selectedSkill === '' ||
                  selectedSkill === profile.skillLevel ||
                  skillUpdate.isPending ||
                  skillUpdate.outcomeUnknown
                }
              >
                {skillUpdate.isPending ? 'Đang cập nhật…' : 'Lưu trình độ'}
              </button>
            </form>

            {skillUpdate.successMessage !== null && (
              <p className="player-success" role="status">
                {skillUpdate.successMessage}
              </p>
            )}
            {skillUpdate.errorMessage !== null && (
              <div className="player-error scoped-player-error" role="alert">
                <p>{skillUpdate.errorMessage}</p>
                {skillUpdate.outcomeUnknown && (
                  <button
                    type="button"
                    disabled={skillUpdate.isPending}
                    onClick={() => void skillUpdate.checkUnknownOutcome()}
                  >
                    Kiểm tra lại dữ liệu
                  </button>
                )}
              </div>
            )}
          </section>

          <section className="player-panel" aria-labelledby="rating-title">
            <h2 id="rating-title">Rating hiện tại</h2>
            <p className="rating-value">
              {formatPlayerRating(profile.rating.ratingValue)}
            </p>
            <dl className="player-details">
              <div>
                <dt>Độ bất định</dt>
                <dd>{formatPlayerRating(profile.rating.uncertainty)}</dd>
              </div>
              <div>
                <dt>Số trận</dt>
                <dd>{ratedMatchesLabel(profile.rating.ratedMatches)}</dd>
              </div>
              <div>
                <dt>Nguồn Rating</dt>
                <dd>{ratingBasisLabel(profile.rating.ratingBasis)}</dd>
              </div>
              {profile.rating.ratingAlgorithmVersion !== null && (
                <div>
                  <dt>Phiên bản thuật toán Rating</dt>
                  <dd>{profile.rating.ratingAlgorithmVersion}</dd>
                </div>
              )}
            </dl>
            <p className="rating-note">
              Rating có thể cập nhật sau khi hệ thống xử lý kết quả trận. Thay đổi
              trình Host đánh giá không đặt lại một Rating đã học.
            </p>
          </section>
        </div>
      )}

      {profile !== undefined && (
        <section
          className="player-panel player-rating-history"
          aria-labelledby="rating-history-title"
        >
          <h2 id="rating-history-title">Lịch sử Rating</h2>
          <p className="rating-note">
            Lịch sử này ghi lại thay đổi Rating sau các trận đã được hệ thống
            xử lý. Rating có thể được cập nhật sau thời điểm trận đấu kết thúc.
          </p>

          {ratingHistoryQuery.isPending ? (
            <p className="player-state">Đang tải lịch sử Rating...</p>
          ) : ratingHistoryQuery.isError ? (
            <div className="player-error scoped-player-error" role="alert">
              <p>Không thể tải lịch sử Rating.</p>
              <button
                type="button"
                onClick={() => void ratingHistoryQuery.refetch()}
              >
                Thử lại
              </button>
            </div>
          ) : ratingHistoryQuery.data.events.length === 0 ? (
            <p className="player-state">Chưa có trận nào được tính Rating.</p>
          ) : (
            <ol className="rating-history-list" aria-label="Các thay đổi Rating">
              {ratingHistoryQuery.data.events.map((historyEvent) => (
                <li
                  className="rating-history-entry"
                  key={`${historyEvent.matchId}-${historyEvent.resultVersion}`}
                >
                  <div className="rating-history-entry-header">
                    <time dateTime={historyEvent.matchCompletedAt}>
                      {formatVietnamDateTime(historyEvent.matchCompletedAt)}
                    </time>
                    <strong
                      className={`rating-outcome rating-outcome-${historyEvent.outcome.toLowerCase()}`}
                    >
                      {ratingOutcomeLabel(historyEvent.outcome)}
                    </strong>
                  </div>
                  <dl className="rating-history-details">
                    <div>
                      <dt>Rating</dt>
                      <dd>
                        {`${formatPlayerRating(historyEvent.beforeRatingValue)} → ${formatPlayerRating(historyEvent.afterRatingValue)}`}
                      </dd>
                    </div>
                    <div>
                      <dt>Thay đổi Rating</dt>
                      <dd>
                        {formatPlayerRatingDelta(
                          historyEvent.beforeRatingValue,
                          historyEvent.afterRatingValue,
                        )}
                      </dd>
                    </div>
                    <div>
                      <dt>Độ bất định</dt>
                      <dd>
                        {`${formatPlayerRating(historyEvent.beforeUncertainty)} → ${formatPlayerRating(historyEvent.afterUncertainty)}`}
                      </dd>
                    </div>
                    <div className="rating-history-diagnostic">
                      <dt>Xử lý Rating</dt>
                      <dd>
                        {`${historyEvent.algorithmVersion} · kết quả v${historyEvent.resultVersion}`}
                      </dd>
                    </div>
                  </dl>
                </li>
              ))}
            </ol>
          )}
        </section>
      )}
    </main>
  )
}

function PlayerDetailFailure({
  message,
  retry,
}: {
  readonly message: string
  readonly retry?: () => void
}) {
  return (
    <main className="player-management">
      <section className="player-panel player-state player-error" role="alert">
        <p>{message}</p>
        {retry !== undefined && <button onClick={retry}>Thử lại</button>}
        <Link to="/players">Về danh sách người chơi</Link>
      </section>
    </main>
  )
}
