import { useMemo } from 'react'
import { useParams } from 'react-router-dom'
import { statusLabel } from '../../lib/presentation'
import type { LiveSessionDataState } from '../live-session/useLiveSessionData'
import { useLiveSessionData } from '../live-session/useLiveSessionData'
import {
  composePlayerSessionViewModel,
  type PlayerSessionDisplayState,
  type PlayerSessionPersonView,
} from './playerSessionModel'
import './PlayerSessionPage.css'

const DISPLAY_STATE_LABELS: Readonly<Record<PlayerSessionDisplayState, string>> = {
  REGISTERED: 'Đã đăng ký',
  WAITING: 'Đang chờ',
  QUEUED: 'Đang trong hàng đợi',
  PLAYING: 'Đang chơi',
  PAUSED: 'Tạm nghỉ',
  LEFT: 'Đã rời',
}

function PersonIdentity({ person }: { readonly person: PlayerSessionPersonView }) {
  return <>{`#${person.participantCode} ${person.displayName}`}</>
}

export function PlayerSessionScreen({
  state,
  sessionParticipantId,
}: {
  readonly state: LiveSessionDataState
  readonly sessionParticipantId: string
}) {
  const model = useMemo(
    () =>
      state.status === 'ready'
        ? composePlayerSessionViewModel(state.data, sessionParticipantId)
        : null,
    [sessionParticipantId, state],
  )

  if (state.status === 'loading') {
    return (
      <main className="player-session-state" aria-live="polite">
        <p className="eyebrow">Trạng thái trong phiên</p>
        <h1>Đang tải trạng thái của bạn…</h1>
      </main>
    )
  }

  if (state.status === 'not-found') {
    return (
      <main className="player-session-state">
        <p className="eyebrow">Trạng thái trong phiên</p>
        <h1>Không tìm thấy phiên</h1>
        <p>Phiên bạn yêu cầu không khả dụng.</p>
      </main>
    )
  }

  if (state.status === 'error') {
    return (
      <main className="player-session-state" role="alert">
        <p className="eyebrow">Trạng thái trong phiên</p>
        <h1>Không thể tải trạng thái trong phiên.</h1>
        <button
          className="player-session-refresh"
          type="button"
          disabled={state.isRefreshing}
          onClick={() => void state.refresh()}
        >
          {state.isRefreshing ? 'Đang thử lại…' : 'Thử lại'}
        </button>
      </main>
    )
  }

  if (model === null) {
    return (
      <main className="player-session-state">
        <p className="eyebrow">Trạng thái trong phiên</p>
        <h1>Không tìm thấy người tham gia</h1>
        <p>Người tham gia không thuộc phiên này hoặc không còn khả dụng.</p>
      </main>
    )
  }

  return (
    <main className="player-session-page">
      <header className="player-session-header">
        <div>
          <p className="eyebrow">Trạng thái trong phiên</p>
          <h1>
            <PersonIdentity person={model.participant} />
          </h1>
          <p>{model.sessionTitle}</p>
        </div>
        <dl>
          <div>
            <dt>Trạng thái của bạn</dt>
            <dd data-player-state={model.displayState}>
              {DISPLAY_STATE_LABELS[model.displayState]}
            </dd>
          </div>
          <div>
            <dt>Trạng thái phiên</dt>
            <dd>{statusLabel(model.sessionStatus)}</dd>
          </div>
        </dl>
      </header>

      {model.activity !== null && (
        <section className="player-session-activity" aria-labelledby="activity-heading">
          <div>
            <p className="eyebrow">Sắp xếp thi đấu</p>
            <h2 id="activity-heading">
              {model.displayState === 'PLAYING'
                ? 'Trận đang chơi'
                : 'Trận trong hàng đợi'}
            </h2>
          </div>
          <dl className="player-session-details">
            <div>
              <dt>Sân</dt>
              <dd>{model.activity.courtName}</dd>
            </div>
            <div>
              <dt>Đồng đội</dt>
              <dd>
                {model.activity.teammate === null ? (
                  'Chưa có dữ liệu'
                ) : (
                  <PersonIdentity person={model.activity.teammate} />
                )}
              </dd>
            </div>
            <div>
              <dt>Đối thủ</dt>
              <dd>
                {model.activity.opponents.length === 0 ? (
                  'Chưa có dữ liệu'
                ) : (
                  <ul aria-label="Đối thủ">
                    {model.activity.opponents.map((opponent) => (
                      <li key={opponent.sessionParticipantId}>
                        <PersonIdentity person={opponent} />
                      </li>
                    ))}
                  </ul>
                )}
              </dd>
            </div>
          </dl>
        </section>
      )}

      {model.detailsIncomplete && (
        <p className="player-session-warning" role="status">
          Một phần thông tin trận chưa đầy đủ. Dữ liệu sẽ được cập nhật lại tự động.
        </p>
      )}
      {state.hasBackgroundError && (
        <p className="player-session-warning" role="status">
          Đang hiển thị dữ liệu gần nhất vì một lần đồng bộ nền chưa thành công.
        </p>
      )}
      <p className="player-session-read-only">
        Đây là màn hình chỉ xem. Mọi thay đổi trạng thái do Host thực hiện.
      </p>
    </main>
  )
}

export function PlayerSessionPage() {
  const { sessionId = '', sessionParticipantId = '' } = useParams()
  const state = useLiveSessionData(sessionId)

  return (
    <PlayerSessionScreen
      state={state}
      sessionParticipantId={sessionParticipantId}
    />
  )
}
