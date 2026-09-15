import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { PARTICIPANT_ACTION_LABELS, statusLabel } from '../../lib/presentation'
import { ParticipantPersonalLinkAction } from '../live-session/ParticipantPersonalLinkAction'
import { useParticipantAction } from '../live-session/useLiveSessionActions'
import {
  composeCheckInParticipants,
  filterCheckInParticipants,
  summarizeCheckInParticipants,
  type CheckInParticipantView,
} from './checkInDeskModel'
import {
  useHostCheckInData,
  type HostCheckInDataState,
} from './useHostCheckInData'
import './HostCheckInPage.css'

function CheckInParticipantRow({
  participant,
  sessionId,
  sessionStatus,
}: {
  readonly participant: CheckInParticipantView
  readonly sessionId: string
  readonly sessionStatus: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
}) {
  const action = useParticipantAction(
    sessionId,
    participant.sessionParticipantId,
  )
  const canCheckIn =
    sessionStatus === 'IN_PROGRESS' && participant.status === 'REGISTERED'
  const participantLabel = `#${participant.participantCode} ${participant.displayName}`
  const statusClassName = participant.status.toLowerCase().replaceAll('_', '-')

  return (
    <li className="check-in-participant-row" aria-label={participantLabel}>
      <div className="check-in-participant-details">
        <strong>{participantLabel}</strong>
        <span className={`status-badge status-${statusClassName}`}>
          {statusLabel(participant.status)}
        </span>
        {participant.playerDataUnavailable && (
          <span className="action-feedback">Thiếu dữ liệu người chơi.</span>
        )}
      </div>
      <div className="check-in-participant-actions">
        {canCheckIn && (
          <button
            className="primary-action-button"
            type="button"
            disabled={action.isPending}
            onClick={() => action.execute('CHECK_IN')}
          >
            {action.pendingAction === 'CHECK_IN'
              ? PARTICIPANT_ACTION_LABELS.CHECK_IN.pending
              : PARTICIPANT_ACTION_LABELS.CHECK_IN.idle}
          </button>
        )}
        {participant.status === 'REGISTERED' &&
          sessionStatus !== 'IN_PROGRESS' && (
            <span className="action-note">
              Phiên phải đang diễn ra để điểm danh.
            </span>
          )}
        <ParticipantPersonalLinkAction
          sessionId={sessionId}
          sessionParticipantId={participant.sessionParticipantId}
          participantLabel={participantLabel}
        />
        {action.errorMessage && (
          <span className="action-feedback" role="alert">
            {action.errorMessage}
          </span>
        )}
      </div>
    </li>
  )
}

export function HostCheckInScreen({
  sessionId,
  state,
}: {
  readonly sessionId: string
  readonly state: HostCheckInDataState
}) {
  const [search, setSearch] = useState('')

  if (state.status === 'loading') {
    return (
      <main className="check-in-desk route-message">
        <h1>Đang tải bàn check-in…</h1>
        <p>Đang tải phiên và danh sách người chơi.</p>
      </main>
    )
  }
  if (state.status === 'not-found') {
    return (
      <main className="check-in-desk route-message">
        <h1>Không tìm thấy phiên</h1>
        <Link to="/">Về trang chủ</Link>
      </main>
    )
  }
  if (state.status === 'error') {
    return (
      <main className="check-in-desk route-message">
        <h1>Không thể tải bàn check-in.</h1>
        <button
          className="refresh-button"
          type="button"
          disabled={state.isRefreshing}
          onClick={() => void state.refresh()}
        >
          {state.isRefreshing ? 'Đang thử lại…' : 'Thử lại'}
        </button>
      </main>
    )
  }

  return (
    <HostCheckInReadyScreen
      sessionId={sessionId}
      state={state}
      search={search}
      onSearchChange={setSearch}
    />
  )
}

function HostCheckInReadyScreen({
  sessionId,
  state,
  search,
  onSearchChange,
}: {
  readonly sessionId: string
  readonly state: Extract<HostCheckInDataState, { readonly status: 'ready' }>
  readonly search: string
  readonly onSearchChange: (value: string) => void
}) {
  const participants = useMemo(
    () => composeCheckInParticipants(state.data.participants, state.data.players),
    [state.data.participants, state.data.players],
  )
  const visibleParticipants = useMemo(
    () => filterCheckInParticipants(participants, search),
    [participants, search],
  )
  const summary = useMemo(
    () => summarizeCheckInParticipants(participants),
    [participants],
  )

  return (
    <main className="check-in-desk">
      <header className="check-in-desk-header">
        <div>
          <p className="eyebrow">Vận hành phiên chơi</p>
          <h1>Bàn check-in</h1>
          <p>{state.data.session.title}</p>
        </div>
        <div className="check-in-desk-navigation">
          <Link to={`/sessions/${sessionId}`}>Quay lại phòng điều hành</Link>
          <button
            className="refresh-button"
            type="button"
            disabled={state.isRefreshing}
            onClick={() => void state.refresh()}
          >
            {state.isRefreshing ? 'Đang làm mới…' : 'Làm mới'}
          </button>
        </div>
      </header>

      {state.hasBackgroundError && (
        <p className="background-refresh-warning" role="status">
          Dữ liệu gần nhất vẫn được giữ lại. Một lần đồng bộ nền chưa thành công;
          bạn có thể dùng “Làm mới”.
        </p>
      )}

      <dl className="check-in-summary" aria-label="Tổng quan check-in">
        <div>
          <dt>Chưa check-in</dt>
          <dd>{summary.registered}</dd>
        </div>
        <div>
          <dt>Đã từng check-in</dt>
          <dd>{summary.checkedIn}</dd>
        </div>
        <div>
          <dt>Tổng người chơi</dt>
          <dd>{summary.total}</dd>
        </div>
      </dl>

      <section className="check-in-participants" aria-labelledby="check-in-list-heading">
        <div className="check-in-list-heading">
          <div>
            <p className="eyebrow">Người chơi trong phiên</p>
            <h2 id="check-in-list-heading">Danh sách check-in</h2>
          </div>
          <span>{visibleParticipants.length} kết quả</span>
        </div>
        <label className="people-search">
          <span>Tìm theo mã hoặc tên</span>
          <input
            type="search"
            value={search}
            placeholder="Ví dụ: #12 hoặc Nguyễn An"
            onChange={(event) => onSearchChange(event.target.value)}
          />
        </label>
        {visibleParticipants.length === 0 ? (
          <p className="people-search-empty">
            Không tìm thấy người chơi trong phiên.
          </p>
        ) : (
          <ul className="check-in-participant-list">
            {visibleParticipants.map((participant) => (
              <CheckInParticipantRow
                key={participant.sessionParticipantId}
                participant={participant}
                sessionId={sessionId}
                sessionStatus={state.data.session.status}
              />
            ))}
          </ul>
        )}
      </section>
    </main>
  )
}

export function HostCheckInPage() {
  const { sessionId = '' } = useParams<{ sessionId: string }>()
  const state = useHostCheckInData(sessionId)

  return <HostCheckInScreen sessionId={sessionId} state={state} />
}
