import { useEffect, useMemo, useState } from 'react'
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

const SESSION_STATUS_LABELS: Readonly<
  Record<LiveSessionModel['header']['status'], string>
> = {
  PLANNED: 'Planned',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
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

function CourtCard({ court }: { readonly court: CourtView }) {
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
    </article>
  )
}

function ParticipantList({
  title,
  participants,
  showWaiting = false,
}: {
  readonly title: string
  readonly participants: readonly ParticipantView[]
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
            <li key={participant.sessionParticipantId}>
              <div>
                <strong>{participant.displayName}</strong>
                <span>{participant.skillLabel ?? 'Skill unavailable'}</span>
              </div>
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
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function CreatedMatchCard({ match }: { readonly match: MatchView }) {
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
              <CourtCard key={court.sessionCourtId} court={court} />
            ))}
          </div>
        )}
      </section>

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
            showWaiting
          />
          <ParticipantList
            title="Registered"
            participants={model.registeredParticipants}
          />
          <ParticipantList
            title="Paused"
            participants={model.pausedParticipants}
          />
          <ParticipantList
            title="Playing"
            participants={model.playingParticipants}
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
                <CreatedMatchCard key={match.id} match={match} />
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
