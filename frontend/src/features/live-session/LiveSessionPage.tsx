import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import {
  composeLiveSessionModel,
  type CourtView,
  type LiveSessionModel,
  type MatchView,
  type ParticipantView,
} from './liveSessionModel'
import './LiveSessionPage.css'
import {
  useLiveSessionData,
  type LiveSessionDataState,
} from './useLiveSessionData'
import {
  useParticipantAction,
  useSessionCourtAction,
  type ParticipantAction,
  type SessionCourtAction,
} from './useLiveSessionActions'
import {
  useCreateManualMatch,
  useMatchLifecycleActions,
} from './useManualMatchActions'
import {
  useSessionLifecycleActions,
  type SessionLifecycleAction,
} from './useSessionLifecycleActions'
import {
  COURT_ACTION_LABELS,
  MATCH_ACTION_LABELS,
  matchFormatLabel,
  PARTICIPANT_ACTION_LABELS,
  SESSION_ACTION_LABELS,
  sportLabel,
  statusLabel,
} from '../../lib/presentation'
import { LiveAddCourt, LiveAddPlayer } from './LiveSessionAdditions'

function isSessionMutable(status: LiveSessionModel['header']['status']) {
  return status === 'PLANNED' || status === 'IN_PROGRESS'
}

function availableParticipantActions(
  participantStatus: ParticipantView['status'],
  sessionStatus: LiveSessionModel['header']['status'],
): readonly ParticipantAction[] {
  if (!isSessionMutable(sessionStatus)) {
    return []
  }
  switch (participantStatus) {
    case 'REGISTERED':
      return sessionStatus === 'IN_PROGRESS'
        ? ['CHECK_IN', 'LEAVE']
        : ['LEAVE']
    case 'WAITING':
      return ['PAUSE', 'LEAVE']
    case 'PAUSED':
      return ['RESUME', 'LEAVE']
    case 'PLAYING':
    case 'LEFT':
      return []
  }
}

function useNow(intervalMilliseconds = 30_000): Date {
  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    const interval = window.setInterval(
      () => setNow(new Date()),
      intervalMilliseconds,
    )
    return () => window.clearInterval(interval)
  }, [intervalMilliseconds])

  return now
}

function StatusBadge({ status }: { readonly status: string }) {
  const statusClassName = status
    .toLowerCase()
    .replaceAll('_', '-')
    .replaceAll(' ', '-')
  return (
    <span className={`status-badge status-${statusClassName}`}>
      {statusLabel(status)}
    </span>
  )
}

function TeamList({
  label,
  members,
}: {
  readonly label: string
  readonly members: MatchView['teamA']
}) {
  return (
    <div className="team">
      <h4>{label}</h4>
      <ul>
        {members.map((member) => (
          <li key={member.slotLabel}>
            <span className="slot-label">{member.slotLabel}</span>
            <span>{member.displayName}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

function MatchTeams({ match }: { readonly match: MatchView }) {
  return (
    <div className="match-teams">
      <TeamList label="Đội A" members={match.teamA} />
      <span className="versus" aria-label="đối đầu">
        đấu
      </span>
      <TeamList label="Đội B" members={match.teamB} />
    </div>
  )
}

type WinnerSelection = '' | 'A' | 'B'

function completeRequest(
  winnerTeam: WinnerSelection,
  teamAScoreInput: string,
  teamBScoreInput: string,
) {
  if (winnerTeam === '') {
    return { error: 'Hãy chọn đội thắng.', request: null }
  }

  const teamAScoreBlank = teamAScoreInput === ''
  const teamBScoreBlank = teamBScoreInput === ''
  if (teamAScoreBlank && teamBScoreBlank) {
    return {
      error: null,
      request: { winnerTeam, teamAScore: null, teamBScore: null },
    }
  }
  if (teamAScoreBlank || teamBScoreBlank) {
    return { error: 'Hãy nhập cả hai tỷ số hoặc để trống cả hai.', request: null }
  }

  const teamAScore = Number(teamAScoreInput)
  const teamBScore = Number(teamBScoreInput)
  if (!Number.isInteger(teamAScore) || !Number.isInteger(teamBScore)) {
    return { error: 'Tỷ số phải là số nguyên.', request: null }
  }
  if (teamAScore < 0 || teamBScore < 0) {
    return { error: 'Tỷ số không được là số âm.', request: null }
  }
  if (teamAScore === teamBScore) {
    return { error: 'Trận đấu không thể kết thúc với tỷ số hòa.', request: null }
  }
  if (
    (winnerTeam === 'A' && teamAScore < teamBScore) ||
    (winnerTeam === 'B' && teamBScore < teamAScore)
  ) {
    return {
      error: 'Đội thắng phải có tỷ số cao hơn.',
      request: null,
    }
  }

  return {
    error: null,
    request: { winnerTeam, teamAScore, teamBScore },
  }
}

function PlayingMatchPanel({
  match,
  sessionId,
}: {
  readonly match: MatchView
  readonly sessionId: string
}) {
  const actionState = useMatchLifecycleActions(sessionId, match.id)
  const [winnerTeam, setWinnerTeam] = useState<WinnerSelection>('')
  const [teamAScore, setTeamAScore] = useState('')
  const [teamBScore, setTeamBScore] = useState('')
  const [validationMessage, setValidationMessage] = useState<string | null>(
    null,
  )
  const [isConfirmingCancel, setIsConfirmingCancel] = useState(false)

  function handleComplete(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (actionState.isPending || isConfirmingCancel) {
      return
    }
    const result = completeRequest(winnerTeam, teamAScore, teamBScore)
    setValidationMessage(result.error)
    if (result.request !== null) {
      void actionState.execute({ type: 'COMPLETE', request: result.request })
    }
  }

  const completeDisabled = actionState.isPending || isConfirmingCancel

  return (
    <div className="court-match">
      <MatchTeams match={match} />
      <dl className="inline-details">
        <div>
          <dt>Nguồn</dt>
          <dd>{match.sourceLabel}</dd>
        </div>
        <div>
          <dt>Bắt đầu</dt>
          <dd>{match.startedAtLabel ?? '—'}</dd>
        </div>
        <div>
          <dt>Thời gian đã chơi</dt>
          <dd>{match.elapsedLabel ?? '—'}</dd>
        </div>
      </dl>
      <form className="complete-match-form" noValidate onSubmit={handleComplete}>
        <div className="result-fields">
          <label className="match-field winner-field">
            <span>Đội thắng</span>
            <select
              value={winnerTeam}
              disabled={completeDisabled}
              onChange={(event) => {
                setWinnerTeam(event.target.value as WinnerSelection)
                setValidationMessage(null)
              }}
            >
              <option value="">Chọn đội thắng</option>
              <option value="A">Đội A</option>
              <option value="B">Đội B</option>
            </select>
          </label>
          <label className="match-field">
            <span>Tỷ số Đội A (không bắt buộc)</span>
            <input
              type="number"
              min="0"
              step="1"
              inputMode="numeric"
              value={teamAScore}
              disabled={completeDisabled}
              onChange={(event) => {
                setTeamAScore(event.target.value)
                setValidationMessage(null)
              }}
            />
          </label>
          <label className="match-field">
            <span>Tỷ số Đội B (không bắt buộc)</span>
            <input
              type="number"
              min="0"
              step="1"
              inputMode="numeric"
              value={teamBScore}
              disabled={completeDisabled}
              onChange={(event) => {
                setTeamBScore(event.target.value)
                setValidationMessage(null)
              }}
            />
          </label>
        </div>
        {validationMessage && (
          <p className="action-feedback" role="alert">
            {validationMessage}
          </p>
        )}
        <div className="match-lifecycle-actions">
          <button
            className="primary-action-button"
            type="submit"
            disabled={completeDisabled}
          >
            {actionState.pendingAction === 'COMPLETE'
              ? MATCH_ACTION_LABELS.COMPLETE.pending
              : MATCH_ACTION_LABELS.COMPLETE.idle}
          </button>
          {!isConfirmingCancel ? (
            <button
              className="danger-action-button"
              type="button"
              disabled={actionState.isPending}
              onClick={() => setIsConfirmingCancel(true)}
            >
              {MATCH_ACTION_LABELS.CANCEL.idle}
            </button>
          ) : (
            <div className="cancel-confirmation">
              <p>Hủy trận đang chơi này? Kết quả sẽ không ghi nhận đội thắng.</p>
              <div className="action-area">
                <button
                  className="danger-action-button"
                  type="button"
                  disabled={actionState.isPending}
                  onClick={() => void actionState.execute({ type: 'CANCEL' })}
                >
                  {actionState.pendingAction === 'CANCEL'
                    ? MATCH_ACTION_LABELS.CANCEL.pending
                    : 'Xác nhận hủy'}
                </button>
                <button
                  className="secondary-action-button"
                  type="button"
                  disabled={actionState.isPending}
                  onClick={() => setIsConfirmingCancel(false)}
                >
                  Giữ trận đấu
                </button>
              </div>
            </div>
          )}
        </div>
        {actionState.errorMessage && (
          <p className="action-feedback" role="alert">
            {actionState.errorMessage}
          </p>
        )}
      </form>
    </div>
  )
}

function CourtCard({
  court,
  sessionId,
  sessionStatus,
}: {
  readonly court: CourtView
  readonly sessionId: string
  readonly sessionStatus: LiveSessionModel['header']['status']
}) {
  const actionState = useSessionCourtAction(sessionId, court.sessionCourtId)
  const action: SessionCourtAction | null = isSessionMutable(sessionStatus)
    ? court.status === 'AVAILABLE'
      ? 'DISABLE'
      : court.status === 'UNAVAILABLE'
        ? 'ENABLE'
        : null
    : null

  return (
    <article className="court-card">
      <header>
        <h3>{court.name}</h3>
        <StatusBadge status={court.status} />
      </header>

      {court.status === 'AVAILABLE' && (
        <p className="court-note">Sẵn sàng thi đấu.</p>
      )}
      {court.status === 'UNAVAILABLE' && (
        <p className="court-note">Tạm khóa trong phiên này.</p>
      )}
      {court.status === 'PLAYING' && court.activeMatch === null && (
        <p className="data-warning">Không có dữ liệu trận đấu trực tiếp.</p>
      )}
      {court.activeMatch && (
        <PlayingMatchPanel match={court.activeMatch} sessionId={sessionId} />
      )}
      {action && (
        <div className="action-area court-action-area">
          <button
            className="secondary-action-button"
            type="button"
            disabled={actionState.isPending}
            onClick={() => actionState.execute(action)}
          >
            {actionState.pendingAction === action
              ? COURT_ACTION_LABELS[action].pending
              : COURT_ACTION_LABELS[action].idle}
          </button>
        </div>
      )}
      {actionState.errorMessage && (
        <p className="action-feedback" role="alert">
          {actionState.errorMessage}
        </p>
      )}
    </article>
  )
}

function ParticipantRow({
  participant,
  sessionId,
  sessionStatus,
  showWaiting,
}: {
  readonly participant: ParticipantView
  readonly sessionId: string
  readonly sessionStatus: LiveSessionModel['header']['status']
  readonly showWaiting: boolean
}) {
  const actionState = useParticipantAction(
    sessionId,
    participant.sessionParticipantId,
  )
  const actions = availableParticipantActions(participant.status, sessionStatus)
  const checkInUnavailable =
    participant.status === 'REGISTERED' && sessionStatus !== 'IN_PROGRESS'

  return (
    <li>
      <div className="participant-identity">
        <strong>{participant.displayName}</strong>
        <span>{participant.skillLabel ?? 'Không có trình độ'}</span>
      </div>
      <div className="participant-operation">
        {showWaiting && (
          <span
            className={
              participant.waitingDuration === null
                ? 'waiting-time data-warning'
                : 'waiting-time'
            }
          >
            {participant.waitingDuration ?? 'Không có thời gian chờ'}
          </span>
        )}
        {actions.length > 0 && (
          <div className="action-area participant-actions">
            {actions.map((action) => (
              <button
                className="secondary-action-button"
                type="button"
                key={action}
                disabled={actionState.isPending}
                onClick={() => actionState.execute(action)}
              >
                {actionState.pendingAction === action
                  ? PARTICIPANT_ACTION_LABELS[action].pending
                  : PARTICIPANT_ACTION_LABELS[action].idle}
              </button>
            ))}
          </div>
        )}
        {checkInUnavailable && (
          <span className="action-note">
            Phiên phải đang diễn ra để điểm danh.
          </span>
        )}
        {actionState.errorMessage && (
          <span className="action-feedback" role="alert">
            {actionState.errorMessage}
          </span>
        )}
      </div>
    </li>
  )
}

function ParticipantList({
  title,
  participants,
  sessionId,
  sessionStatus,
  showWaiting = false,
}: {
  readonly title: string
  readonly participants: readonly ParticipantView[]
  readonly sessionId: string
  readonly sessionStatus: LiveSessionModel['header']['status']
  readonly showWaiting?: boolean
}) {
  return (
    <section className="participant-group">
      <div className="section-title">
        <h3>{title}</h3>
        <span>{participants.length}</span>
      </div>
      {participants.length === 0 ? (
        <p className="empty-state">Không có người chơi.</p>
      ) : (
        <ul className="participant-list">
          {participants.map((participant) => (
            <ParticipantRow
              key={participant.sessionParticipantId}
              participant={participant}
              sessionId={sessionId}
              sessionStatus={sessionStatus}
              showWaiting={showWaiting}
            />
          ))}
        </ul>
      )}
    </section>
  )
}

function participantOptionLabel(participant: ParticipantView): string {
  return [
    participant.displayName,
    participant.skillLabel ?? 'Không có trình độ',
    participant.waitingDuration === null
      ? 'không có thời gian chờ'
      : `đã chờ ${participant.waitingDuration}`,
  ].join(' · ')
}

type MatchSlot = 'A1' | 'A2' | 'B1' | 'B2'

const MATCH_SLOTS: readonly {
  readonly id: MatchSlot
  readonly label: string
  readonly teamSide: 'A' | 'B'
  readonly teamSlot: 1 | 2
}[] = [
  { id: 'A1', label: 'Đội A — Vị trí 1', teamSide: 'A', teamSlot: 1 },
  { id: 'A2', label: 'Đội A — Vị trí 2', teamSide: 'A', teamSlot: 2 },
  { id: 'B1', label: 'Đội B — Vị trí 1', teamSide: 'B', teamSlot: 1 },
  { id: 'B2', label: 'Đội B — Vị trí 2', teamSide: 'B', teamSlot: 2 },
]

const EMPTY_MATCH_SLOTS: Readonly<Record<MatchSlot, string>> = {
  A1: '',
  A2: '',
  B1: '',
  B2: '',
}

function CreateManualMatchForm({
  sessionId,
  model,
}: {
  readonly sessionId: string
  readonly model: LiveSessionModel
}) {
  const actionState = useCreateManualMatch(sessionId)
  const [sessionCourtId, setSessionCourtId] = useState('')
  const [participantsBySlot, setParticipantsBySlot] = useState(
    EMPTY_MATCH_SLOTS,
  )
  const availableCourts = model.courts.filter(
    (court) => court.status === 'AVAILABLE',
  )
  const waitingParticipants = model.waitingParticipants
  const availableCourtIds = new Set(
    availableCourts.map((court) => court.sessionCourtId),
  )
  const waitingParticipantIds = new Set(
    waitingParticipants.map((participant) => participant.sessionParticipantId),
  )

  if (model.header.status !== 'IN_PROGRESS') {
    return (
      <section className="panel manual-match-creator" aria-labelledby="create-match-heading">
        <div className="section-title section-title-large">
          <div>
            <p className="eyebrow">Vận hành trận đấu</p>
            <h2 id="create-match-heading">Tạo trận thủ công</h2>
          </div>
        </div>
        <p className="empty-panel">
          Chỉ có thể tạo trận thủ công khi phiên đang diễn ra.
        </p>
      </section>
    )
  }

  const selectedParticipants = Object.values(participantsBySlot).filter(
    (participantId) => participantId !== '',
  )
  const selectionIsUnique =
    new Set(selectedParticipants).size === selectedParticipants.length
  const hasStaleSelection =
    (sessionCourtId !== '' && !availableCourtIds.has(sessionCourtId)) ||
    selectedParticipants.some(
      (participantId) => !waitingParticipantIds.has(participantId),
    )
  const formIsValid =
    availableCourtIds.has(sessionCourtId) &&
    selectedParticipants.length === MATCH_SLOTS.length &&
    selectionIsUnique &&
    selectedParticipants.every((participantId) =>
      waitingParticipantIds.has(participantId),
    )

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!formIsValid || actionState.isPending) {
      return
    }

    const succeeded = await actionState.execute({
      sessionCourtId,
      participants: MATCH_SLOTS.map((slot) => ({
        sessionParticipantId: participantsBySlot[slot.id],
        teamSide: slot.teamSide,
        teamSlot: slot.teamSlot,
      })),
    })
    if (succeeded) {
      setSessionCourtId('')
      setParticipantsBySlot(EMPTY_MATCH_SLOTS)
    }
  }

  return (
    <section className="panel manual-match-creator" aria-labelledby="create-match-heading">
      <div className="section-title section-title-large">
        <div>
          <p className="eyebrow">Vận hành trận đấu</p>
          <h2 id="create-match-heading">Tạo trận thủ công</h2>
        </div>
      </div>
      <form onSubmit={(event) => void handleSubmit(event)}>
        <div className="manual-match-fields">
          <label className="match-field court-field">
            <span>Sân trong phiên</span>
            <select
              value={
                availableCourtIds.has(sessionCourtId) ? sessionCourtId : ''
              }
              disabled={actionState.isPending || availableCourts.length === 0}
              onChange={(event) => setSessionCourtId(event.target.value)}
            >
              <option value="">Chọn sân sẵn sàng</option>
              {availableCourts.map((court) => (
                <option key={court.sessionCourtId} value={court.sessionCourtId}>
                  {court.name}
                </option>
              ))}
            </select>
          </label>
          <div className="team-fields" aria-label="Phân công Đội A">
            {MATCH_SLOTS.filter((slot) => slot.teamSide === 'A').map((slot) => (
              <label className="match-field" key={slot.id}>
                <span>{slot.label}</span>
                <select
                  value={
                    waitingParticipantIds.has(participantsBySlot[slot.id])
                      ? participantsBySlot[slot.id]
                      : ''
                  }
                  disabled={actionState.isPending || waitingParticipants.length < 4}
                  onChange={(event) =>
                    setParticipantsBySlot((current) => ({
                      ...current,
                      [slot.id]: event.target.value,
                    }))
                  }
                >
                  <option value="">Chọn người chơi đang chờ</option>
                  {waitingParticipants.map((participant) => {
                    const selectedElsewhere = MATCH_SLOTS.some(
                      (candidateSlot) =>
                        candidateSlot.id !== slot.id &&
                        participantsBySlot[candidateSlot.id] ===
                          participant.sessionParticipantId,
                    )
                    return (
                      <option
                        key={participant.sessionParticipantId}
                        value={participant.sessionParticipantId}
                        disabled={selectedElsewhere}
                      >
                        {participantOptionLabel(participant)}
                      </option>
                    )
                  })}
                </select>
              </label>
            ))}
          </div>
          <div className="team-fields" aria-label="Phân công Đội B">
            {MATCH_SLOTS.filter((slot) => slot.teamSide === 'B').map((slot) => (
              <label className="match-field" key={slot.id}>
                <span>{slot.label}</span>
                <select
                  value={
                    waitingParticipantIds.has(participantsBySlot[slot.id])
                      ? participantsBySlot[slot.id]
                      : ''
                  }
                  disabled={actionState.isPending || waitingParticipants.length < 4}
                  onChange={(event) =>
                    setParticipantsBySlot((current) => ({
                      ...current,
                      [slot.id]: event.target.value,
                    }))
                  }
                >
                  <option value="">Chọn người chơi đang chờ</option>
                  {waitingParticipants.map((participant) => {
                    const selectedElsewhere = MATCH_SLOTS.some(
                      (candidateSlot) =>
                        candidateSlot.id !== slot.id &&
                        participantsBySlot[candidateSlot.id] ===
                          participant.sessionParticipantId,
                    )
                    return (
                      <option
                        key={participant.sessionParticipantId}
                        value={participant.sessionParticipantId}
                        disabled={selectedElsewhere}
                      >
                        {participantOptionLabel(participant)}
                      </option>
                    )
                  })}
                </select>
              </label>
            ))}
          </div>
        </div>
        {availableCourts.length === 0 && (
          <p className="form-note">Không có sân sẵn sàng để chọn.</p>
        )}
        {waitingParticipants.length < 4 && (
          <p className="form-note">
            Cần ít nhất bốn người chơi đang chờ để tạo trận.
          </p>
        )}
        {hasStaleSelection && (
          <p className="form-note" role="status">
            Lựa chọn trước đó không còn hợp lệ. Hãy chọn lại từ các tùy chọn
            hiện tại trước khi tạo trận.
          </p>
        )}
        <p className="form-note">
          Việc tạo trận chưa giữ sân hoặc người chơi. Hệ thống sẽ kiểm tra lại
          trạng thái sẵn sàng khi trận bắt đầu.
        </p>
        <div className="create-match-actions">
          <button
            className="primary-action-button"
            type="submit"
            disabled={!formIsValid || actionState.isPending}
          >
            {actionState.isPending ? 'Đang tạo…' : 'Tạo trận'}
          </button>
        </div>
        {actionState.errorMessage && (
          <p className="action-feedback" role="alert">
            {actionState.errorMessage}
          </p>
        )}
      </form>
    </section>
  )
}

function CreatedMatchCard({
  match,
  sessionId,
  sessionStatus,
}: {
  readonly match: MatchView
  readonly sessionId: string
  readonly sessionStatus: LiveSessionModel['header']['status']
}) {
  const actionState = useMatchLifecycleActions(sessionId, match.id)
  const [isConfirmingCancel, setIsConfirmingCancel] = useState(false)
  const canStart = sessionStatus === 'IN_PROGRESS'

  return (
    <article className="created-match-card">
      <header>
        <div>
          <h3>{match.courtName}</h3>
          <p>Đã tạo — chưa bắt đầu</p>
        </div>
        <span className="source-label">{match.sourceLabel}</span>
      </header>
      <MatchTeams match={match} />
      <p className="created-time">Tạo lúc {match.createdAtLabel}</p>
      <div className="action-area created-match-actions">
        {canStart && (
          <button
            className="primary-action-button"
            type="button"
            disabled={actionState.isPending || isConfirmingCancel}
            onClick={() => void actionState.execute({ type: 'START' })}
          >
            {actionState.pendingAction === 'START'
              ? MATCH_ACTION_LABELS.START.pending
              : MATCH_ACTION_LABELS.START.idle}
          </button>
        )}
        {!isConfirmingCancel && (
          <button
            className="danger-action-button"
            type="button"
            disabled={actionState.isPending}
            onClick={() => setIsConfirmingCancel(true)}
          >
            {MATCH_ACTION_LABELS.CANCEL.idle}
          </button>
        )}
      </div>
      {!canStart && (
        <p className="action-note">
          Trận này chỉ có thể bắt đầu khi phiên đang diễn ra.
        </p>
      )}
      {isConfirmingCancel && (
        <div className="cancel-confirmation">
          <p>Hủy trận đã tạo này?</p>
          <div className="action-area">
            <button
              className="danger-action-button"
              type="button"
              disabled={actionState.isPending}
              onClick={() => void actionState.execute({ type: 'CANCEL' })}
            >
              {actionState.pendingAction === 'CANCEL'
                ? MATCH_ACTION_LABELS.CANCEL.pending
                : 'Xác nhận hủy'}
            </button>
            <button
              className="secondary-action-button"
              type="button"
              disabled={actionState.isPending}
              onClick={() => setIsConfirmingCancel(false)}
            >
              Giữ trận đấu
            </button>
          </div>
        </div>
      )}
      {actionState.errorMessage && (
        <p className="action-feedback" role="alert">
          {actionState.errorMessage}
        </p>
      )}
    </article>
  )
}

function SessionHeader({
  model,
  sessionId,
  hasPlayingMatch,
  onRefresh,
  isRefreshing,
}: {
  readonly model: LiveSessionModel
  readonly sessionId: string
  readonly hasPlayingMatch: boolean
  readonly onRefresh: () => Promise<void>
  readonly isRefreshing: boolean
}) {
  return (
    <header className="session-header">
      <div>
        <p className="eyebrow">Phòng điều hành phiên trực tiếp</p>
        <h1>{model.header.title}</h1>
        <p className="venue-line">
          {model.header.venueName}
          {model.header.venueLocation
            ? ` · ${model.header.venueLocation}`
            : ''}
        </p>
      </div>
      <button
        className="refresh-button"
        type="button"
        disabled={isRefreshing}
        onClick={() => void onRefresh()}
      >
        {isRefreshing ? 'Đang làm mới…' : 'Làm mới'}
      </button>
      <dl className="session-facts">
        <div>
          <dt>Trạng thái</dt>
          <dd>
            <StatusBadge status={model.header.status} />
          </dd>
        </div>
        <div>
          <dt>Môn thể thao</dt>
          <dd>{sportLabel(model.header.sport)}</dd>
        </div>
        <div>
          <dt>Thể thức</dt>
          <dd>{matchFormatLabel(model.header.matchFormat)}</dd>
        </div>
        <div>
          <dt>Dự kiến</dt>
          <dd>
            {model.header.plannedStartAtLabel} – {model.header.plannedEndAtLabel}
          </dd>
        </div>
        <div>
          <dt>Bắt đầu</dt>
          <dd>{model.header.startedAtLabel ?? 'Chưa bắt đầu'}</dd>
        </div>
      </dl>
      <SessionLifecycleControls
        sessionId={sessionId}
        sessionStatus={model.header.status}
        hasPlayingMatch={hasPlayingMatch}
      />
    </header>
  )
}

function SessionLifecycleControls({
  sessionId,
  sessionStatus,
  hasPlayingMatch,
}: {
  readonly sessionId: string
  readonly sessionStatus: LiveSessionModel['header']['status']
  readonly hasPlayingMatch: boolean
}) {
  const actionState = useSessionLifecycleActions(sessionId)
  const [confirmation, setConfirmation] =
    useState<SessionLifecycleAction | null>(null)

  if (sessionStatus === 'COMPLETED' || sessionStatus === 'CANCELLED') {
    return null
  }

  const completeIsAvailable = sessionStatus === 'IN_PROGRESS'
  const activeConfirmation =
    confirmation === 'COMPLETE' &&
    (!completeIsAvailable || hasPlayingMatch)
      ? null
      : confirmation
  const controlsLocked = actionState.isPending || activeConfirmation !== null
  const confirmationMessage =
    activeConfirmation === 'COMPLETE'
      ? 'Kết thúc phiên này? Thao tác cuối cùng này không thể hoàn tác.'
      : hasPlayingMatch
        ? 'Hủy phiên này? Phiên sẽ bị hủy nhưng trận đang chơi không tự kết thúc. Sau đó, bạn vẫn phải kết thúc hoặc hủy trận để giải phóng sân và người chơi.'
        : 'Hủy phiên này? Thao tác cuối cùng này không thể hoàn tác.'

  function executeConfirmedAction() {
    if (activeConfirmation === null) {
      return
    }
    const action = activeConfirmation
    setConfirmation(null)
    actionState.execute(action)
  }

  return (
    <section
      className="session-lifecycle-controls"
      aria-labelledby="session-lifecycle-heading"
    >
      <div>
        <p className="eyebrow">Vận hành phiên chơi</p>
        <h2 id="session-lifecycle-heading">Kết thúc phiên</h2>
      </div>
      <div className="session-lifecycle-operation">
        <div className="action-area session-lifecycle-actions">
          {completeIsAvailable && (
            <button
              className="primary-action-button"
              type="button"
              disabled={controlsLocked || hasPlayingMatch}
              onClick={() => setConfirmation('COMPLETE')}
            >
              {actionState.pendingAction === 'COMPLETE'
                ? SESSION_ACTION_LABELS.COMPLETE.pending
                : SESSION_ACTION_LABELS.COMPLETE.idle}
            </button>
          )}
          <button
            className="danger-action-button"
            type="button"
            disabled={controlsLocked}
            onClick={() => setConfirmation('CANCEL')}
          >
            {actionState.pendingAction === 'CANCEL'
              ? SESSION_ACTION_LABELS.CANCEL.pending
              : SESSION_ACTION_LABELS.CANCEL.idle}
          </button>
        </div>
        {completeIsAvailable && hasPlayingMatch && (
          <p className="session-lifecycle-note" role="status">
            Không thể kết thúc phiên khi đang có trận thi đấu. Hãy kết thúc
            hoặc hủy trận đang chơi trước.
          </p>
        )}
        {activeConfirmation !== null && (
          <div className="session-lifecycle-confirmation">
            <p>{confirmationMessage}</p>
            <div className="action-area">
              <button
                className={
                  activeConfirmation === 'CANCEL'
                    ? 'danger-action-button'
                    : 'primary-action-button'
                }
                type="button"
                disabled={actionState.isPending}
                onClick={executeConfirmedAction}
              >
                {actionState.pendingAction === 'COMPLETE'
                  ? SESSION_ACTION_LABELS.COMPLETE.pending
                  : actionState.pendingAction === 'CANCEL'
                    ? SESSION_ACTION_LABELS.CANCEL.pending
                    : activeConfirmation === 'COMPLETE'
                      ? 'Xác nhận kết thúc'
                      : 'Xác nhận hủy'}
              </button>
              <button
                className="secondary-action-button"
                type="button"
                disabled={actionState.isPending}
                onClick={() => setConfirmation(null)}
              >
                Giữ phiên
              </button>
            </div>
          </div>
        )}
        {actionState.errorMessage && (
          <p className="action-feedback" role="alert">
            {actionState.errorMessage}
          </p>
        )}
      </div>
    </section>
  )
}

export function LiveSessionScreen({
  state,
  now,
}: {
  readonly state: LiveSessionDataState
  readonly now: Date
}) {
  const model = useMemo(
    () =>
      state.status === 'ready'
        ? composeLiveSessionModel({ ...state.data, now })
        : null,
    [now, state],
  )

  if (state.status === 'loading') {
    return (
      <main className="route-state" aria-live="polite">
        <p className="eyebrow">Phòng điều hành phiên trực tiếp</p>
        <h1>Đang tải phiên…</h1>
        <p>Đang tải sân, người chơi và trận đấu.</p>
      </main>
    )
  }

  if (state.status === 'not-found') {
    return (
      <main className="route-state">
        <p className="eyebrow">Phòng điều hành phiên trực tiếp</p>
        <h1>Không tìm thấy phiên</h1>
        <p>Phiên bạn yêu cầu không khả dụng.</p>
      </main>
    )
  }

  if (state.status === 'error' || model === null) {
    return (
      <main className="route-state" role="alert">
        <p className="eyebrow">Phòng điều hành phiên trực tiếp</p>
        <h1>Không thể tải dữ liệu phiên trực tiếp.</h1>
        <p>Một hoặc nhiều dữ liệu bắt buộc không tải được. Hãy thử lại.</p>
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
    <main className="control-room">
      <SessionHeader
        model={model}
        sessionId={state.data.session.id}
        hasPlayingMatch={state.data.matches.some(
          (match) => match.status === 'PLAYING',
        )}
        onRefresh={state.refresh}
        isRefreshing={state.isRefreshing}
      />

      {model.warnings.length > 0 && (
        <aside className="consistency-warning" aria-live="polite">
          <strong>Dữ liệu trực tiếp có thể chưa đồng bộ. Hãy làm mới.</strong>
          <ul>
            {model.warnings.map((warning) => (
              <li key={warning}>{warning}</li>
            ))}
          </ul>
        </aside>
      )}

      <section className="court-board" aria-labelledby="court-board-heading">
        <div className="section-title section-title-large">
          <div>
            <p className="eyebrow">Khu vực thi đấu</p>
            <h2 id="court-board-heading">Bảng sân</h2>
          </div>
          <span>{model.courts.length} sân</span>
        </div>
        <LiveAddCourt
          sessionId={state.data.session.id}
          sessionStatus={model.header.status}
          venueId={state.data.session.venueId}
          venueCourts={state.data.venueCourts}
          sessionCourts={state.data.sessionCourts}
        />
        {model.courts.length === 0 ? (
          <p className="empty-panel">Chưa có sân nào trong phiên này.</p>
        ) : (
          <div className="court-grid">
            {model.courts.map((court) => (
              <CourtCard
                key={court.sessionCourtId}
                court={court}
                sessionId={state.data.session.id}
                sessionStatus={model.header.status}
              />
            ))}
          </div>
        )}
      </section>

      <CreateManualMatchForm
        sessionId={state.data.session.id}
        model={model}
      />

      <div className="operational-grid">
        <section className="panel participant-panel" aria-labelledby="participants-heading">
          <div className="section-title section-title-large">
            <div>
              <p className="eyebrow">Người chơi</p>
              <h2 id="participants-heading">Người chơi</h2>
            </div>
          </div>
          <LiveAddPlayer
            sessionId={state.data.session.id}
            sessionStatus={model.header.status}
            players={state.data.players}
            participants={state.data.participants}
          />
          <ParticipantList
            title="Đang chờ"
            participants={model.waitingParticipants}
            sessionId={state.data.session.id}
            sessionStatus={model.header.status}
            showWaiting
          />
          <ParticipantList
            title="Đã đăng ký"
            participants={model.registeredParticipants}
            sessionId={state.data.session.id}
            sessionStatus={model.header.status}
          />
          <ParticipantList
            title="Tạm nghỉ"
            participants={model.pausedParticipants}
            sessionId={state.data.session.id}
            sessionStatus={model.header.status}
          />
          <ParticipantList
            title="Đang chơi"
            participants={model.playingParticipants}
            sessionId={state.data.session.id}
            sessionStatus={model.header.status}
          />
          {model.leftParticipantCount > 0 && (
            <p className="left-count">
              {model.leftParticipantCount} người đã rời phiên
            </p>
          )}
        </section>

        <section className="panel created-matches" aria-labelledby="created-matches-heading">
          <div className="section-title section-title-large">
            <div>
              <p className="eyebrow">Hàng chờ bền vững</p>
              <h2 id="created-matches-heading">Trận chờ bắt đầu</h2>
            </div>
            <span>{model.createdMatches.length}</span>
          </div>
          {model.createdMatches.length === 0 ? (
            <p className="empty-panel">Không có trận nào đang chờ bắt đầu.</p>
          ) : (
            <div className="created-match-list">
              {model.createdMatches.map((match) => (
                <CreatedMatchCard
                  key={match.id}
                  match={match}
                  sessionId={state.data.session.id}
                  sessionStatus={model.header.status}
                />
              ))}
            </div>
          )}
          {model.resolvedMatchCount > 0 && (
            <p className="resolved-count">
              {model.resolvedMatchCount} trận đã kết thúc hoặc bị hủy
            </p>
          )}
        </section>
      </div>
    </main>
  )
}

export function LiveSessionPage() {
  const { sessionId = '' } = useParams()
  const state = useLiveSessionData(sessionId)
  const now = useNow()

  return <LiveSessionScreen state={state} now={now} />
}
