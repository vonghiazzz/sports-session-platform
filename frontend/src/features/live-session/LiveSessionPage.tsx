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
  useStartMatch,
} from './useManualMatchActions'

const SESSION_STATUS_LABELS: Readonly<
  Record<LiveSessionModel['header']['status'], string>
> = {
  PLANNED: 'Planned',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
}

const PARTICIPANT_ACTION_LABELS: Readonly<
  Record<ParticipantAction, { readonly idle: string; readonly pending: string }>
> = {
  CHECK_IN: { idle: 'Check In', pending: 'Checking in…' },
  PAUSE: { idle: 'Pause', pending: 'Pausing…' },
  RESUME: { idle: 'Resume', pending: 'Resuming…' },
  LEAVE: { idle: 'Leave', pending: 'Leaving…' },
}

const COURT_ACTION_LABELS: Readonly<
  Record<SessionCourtAction, { readonly idle: string; readonly pending: string }>
> = {
  DISABLE: { idle: 'Disable Court', pending: 'Disabling…' },
  ENABLE: { idle: 'Enable Court', pending: 'Enabling…' },
}

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
  const statusClassName = status.toLowerCase().replaceAll(' ', '-')
  return (
    <span className={`status-badge status-${statusClassName}`}>
      {status.replaceAll('_', ' ')}
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
      <TeamList label="Team A" members={match.teamA} />
      <span className="versus" aria-label="versus">
        vs
      </span>
      <TeamList label="Team B" members={match.teamB} />
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
        <p className="court-note">Ready for play.</p>
      )}
      {court.status === 'UNAVAILABLE' && (
        <p className="court-note">Unavailable for this Session.</p>
      )}
      {court.status === 'PLAYING' && court.activeMatch === null && (
        <p className="data-warning">Live match data unavailable.</p>
      )}
      {court.activeMatch && (
        <div className="court-match">
          <MatchTeams match={court.activeMatch} />
          <dl className="inline-details">
            <div>
              <dt>Source</dt>
              <dd>{court.activeMatch.sourceLabel}</dd>
            </div>
            <div>
              <dt>Started</dt>
              <dd>{court.activeMatch.startedAtLabel ?? 'Time unavailable'}</dd>
            </div>
            <div>
              <dt>Elapsed</dt>
              <dd>{court.activeMatch.elapsedLabel ?? 'Time unavailable'}</dd>
            </div>
          </dl>
        </div>
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
        <span>{participant.skillLabel ?? 'Skill unavailable'}</span>
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
            {participant.waitingDuration ?? 'Waiting time unavailable'}
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
            Session must be in progress to check in.
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
        <p className="empty-state">No players.</p>
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
    participant.skillLabel ?? 'Skill unavailable',
    participant.waitingDuration === null
      ? 'waiting time unavailable'
      : `waiting ${participant.waitingDuration}`,
  ].join(' · ')
}

type MatchSlot = 'A1' | 'A2' | 'B1' | 'B2'

const MATCH_SLOTS: readonly {
  readonly id: MatchSlot
  readonly label: string
  readonly teamSide: 'A' | 'B'
  readonly teamSlot: 1 | 2
}[] = [
  { id: 'A1', label: 'Team A — Slot 1', teamSide: 'A', teamSlot: 1 },
  { id: 'A2', label: 'Team A — Slot 2', teamSide: 'A', teamSlot: 2 },
  { id: 'B1', label: 'Team B — Slot 1', teamSide: 'B', teamSlot: 1 },
  { id: 'B2', label: 'Team B — Slot 2', teamSide: 'B', teamSlot: 2 },
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
            <p className="eyebrow">Match operations</p>
            <h2 id="create-match-heading">Create Manual Match</h2>
          </div>
        </div>
        <p className="empty-panel">
          Manual Matches can only be created while the Session is in progress.
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
          <p className="eyebrow">Match operations</p>
          <h2 id="create-match-heading">Create Manual Match</h2>
        </div>
      </div>
      <form onSubmit={(event) => void handleSubmit(event)}>
        <div className="manual-match-fields">
          <label className="match-field court-field">
            <span>Session Court</span>
            <select
              value={
                availableCourtIds.has(sessionCourtId) ? sessionCourtId : ''
              }
              disabled={actionState.isPending || availableCourts.length === 0}
              onChange={(event) => setSessionCourtId(event.target.value)}
            >
              <option value="">Choose an available Court</option>
              {availableCourts.map((court) => (
                <option key={court.sessionCourtId} value={court.sessionCourtId}>
                  {court.name}
                </option>
              ))}
            </select>
          </label>
          <div className="team-fields" aria-label="Team A assignments">
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
                  <option value="">Choose a waiting player</option>
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
          <div className="team-fields" aria-label="Team B assignments">
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
                  <option value="">Choose a waiting player</option>
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
          <p className="form-note">No AVAILABLE Court can be selected.</p>
        )}
        {waitingParticipants.length < 4 && (
          <p className="form-note">
            At least four WAITING players are needed to create a Match.
          </p>
        )}
        {hasStaleSelection && (
          <p className="form-note" role="status">
            A previous selection is no longer eligible. Choose from the current
            options before creating.
          </p>
        )}
        <p className="form-note">
          Creating a Match does not reserve its Court or players. Availability
          is checked again when the Match starts.
        </p>
        <div className="create-match-actions">
          <button
            className="primary-action-button"
            type="submit"
            disabled={!formIsValid || actionState.isPending}
          >
            {actionState.isPending ? 'Creating…' : 'Create Match'}
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
  const actionState = useStartMatch(sessionId, match.id)
  const canStart = sessionStatus === 'IN_PROGRESS'

  return (
    <article className="created-match-card">
      <header>
        <div>
          <h3>{match.courtName}</h3>
          <p>Created — not started</p>
        </div>
        <span className="source-label">{match.sourceLabel}</span>
      </header>
      <MatchTeams match={match} />
      <p className="created-time">Created {match.createdAtLabel}</p>
      {canStart ? (
        <div className="action-area created-match-actions">
          <button
            className="primary-action-button"
            type="button"
            disabled={actionState.isPending}
            onClick={() => void actionState.execute()}
          >
            {actionState.isPending ? 'Starting…' : 'Start Match'}
          </button>
        </div>
      ) : (
        <p className="action-note">
          This Match can only start while the Session is in progress.
        </p>
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
  onRefresh,
  isRefreshing,
}: {
  readonly model: LiveSessionModel
  readonly onRefresh: () => Promise<void>
  readonly isRefreshing: boolean
}) {
  return (
    <header className="session-header">
      <div>
        <p className="eyebrow">Host Live Session Control Room</p>
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
        {isRefreshing ? 'Refreshing…' : 'Refresh'}
      </button>
      <dl className="session-facts">
        <div>
          <dt>Status</dt>
          <dd>
            <StatusBadge status={SESSION_STATUS_LABELS[model.header.status]} />
          </dd>
        </div>
        <div>
          <dt>Sport</dt>
          <dd>{model.header.sport === 'BADMINTON' ? 'Badminton' : model.header.sport}</dd>
        </div>
        <div>
          <dt>Format</dt>
          <dd>{model.header.matchFormat === 'DOUBLES' ? 'Doubles' : model.header.matchFormat}</dd>
        </div>
        <div>
          <dt>Planned</dt>
          <dd>
            {model.header.plannedStartAtLabel} – {model.header.plannedEndAtLabel}
          </dd>
        </div>
        <div>
          <dt>Started</dt>
          <dd>{model.header.startedAtLabel ?? 'Not started'}</dd>
        </div>
      </dl>
    </header>
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
        <p className="eyebrow">Host Live Session Control Room</p>
        <h1>Loading Session…</h1>
        <p>Gathering Courts, Players, and Matches.</p>
      </main>
    )
  }

  if (state.status === 'not-found') {
    return (
      <main className="route-state">
        <p className="eyebrow">Host Live Session Control Room</p>
        <h1>Session not found</h1>
        <p>The requested Session is not available.</p>
      </main>
    )
  }

  if (state.status === 'error' || model === null) {
    return (
      <main className="route-state" role="alert">
        <p className="eyebrow">Host Live Session Control Room</p>
        <h1>Unable to load live Session data.</h1>
        <p>One or more required reads failed. Try again.</p>
        <button
          className="refresh-button"
          type="button"
          disabled={state.isRefreshing}
          onClick={() => void state.refresh()}
        >
          {state.isRefreshing ? 'Retrying…' : 'Retry'}
        </button>
      </main>
    )
  }

  return (
    <main className="control-room">
      <SessionHeader
        model={model}
        onRefresh={state.refresh}
        isRefreshing={state.isRefreshing}
      />

      {model.warnings.length > 0 && (
        <aside className="consistency-warning" aria-live="polite">
          <strong>Live data may be out of sync. Refresh.</strong>
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
            <p className="eyebrow">Live floor</p>
            <h2 id="court-board-heading">Court Board</h2>
          </div>
          <span>{model.courts.length} Courts</span>
        </div>
        {model.courts.length === 0 ? (
          <p className="empty-panel">No Courts are attached to this Session.</p>
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
              <p className="eyebrow">People</p>
              <h2 id="participants-heading">Participants</h2>
            </div>
          </div>
          <ParticipantList
            title="Waiting"
            participants={model.waitingParticipants}
            sessionId={state.data.session.id}
            sessionStatus={model.header.status}
            showWaiting
          />
          <ParticipantList
            title="Registered"
            participants={model.registeredParticipants}
            sessionId={state.data.session.id}
            sessionStatus={model.header.status}
          />
          <ParticipantList
            title="Paused"
            participants={model.pausedParticipants}
            sessionId={state.data.session.id}
            sessionStatus={model.header.status}
          />
          <ParticipantList
            title="Playing"
            participants={model.playingParticipants}
            sessionId={state.data.session.id}
            sessionStatus={model.header.status}
          />
          {model.leftParticipantCount > 0 && (
            <p className="left-count">{model.leftParticipantCount} left this Session</p>
          )}
        </section>

        <section className="panel created-matches" aria-labelledby="created-matches-heading">
          <div className="section-title section-title-large">
            <div>
              <p className="eyebrow">Durable queue</p>
              <h2 id="created-matches-heading">Created Matches</h2>
            </div>
            <span>{model.createdMatches.length}</span>
          </div>
          {model.createdMatches.length === 0 ? (
            <p className="empty-panel">No Matches are waiting to start.</p>
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
              {model.resolvedMatchCount} completed or cancelled Match
              {model.resolvedMatchCount === 1 ? '' : 'es'}
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
