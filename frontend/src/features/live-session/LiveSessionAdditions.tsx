import { useMemo, useState, type FormEvent } from 'react'
import type {
  CourtResponse,
  PlayerResponse,
  SessionCourtResponse,
  SessionParticipantResponse,
  SessionStatus,
  SkillLevel,
} from '../../api/contracts'
import { skillLevelLabel } from '../../lib/presentation'
import {
  useLiveAddCourt,
  useLiveAddPlayer,
} from './useLiveSessionAdditions'

const SKILL_LEVELS: readonly SkillLevel[] = [
  'WEAK',
  'WEAK_PLUS',
  'INTERMEDIATE_MINUS',
  'INTERMEDIATE',
  'INTERMEDIATE_PLUS',
  'GOOD',
]

export function LiveAddPlayer({
  sessionId,
  sessionStatus,
  players,
  participants,
}: {
  readonly sessionId: string
  readonly sessionStatus: SessionStatus
  readonly players: readonly PlayerResponse[]
  readonly participants: readonly SessionParticipantResponse[]
}) {
  const action = useLiveAddPlayer(sessionId)
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [selectedPlayerId, setSelectedPlayerId] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [displayName, setDisplayName] = useState('')
  const [skillLevel, setSkillLevel] = useState<SkillLevel>('INTERMEDIATE')

  const participantPlayerIds = useMemo(
    () => new Set(participants.map((participant) => participant.playerId)),
    [participants],
  )
  const normalizedSearch = search.trim().toLocaleLowerCase('vi-VN')
  const eligiblePlayers = players.filter(
    (player) =>
      !participantPlayerIds.has(player.id) &&
      player.sportProfiles.some((profile) => profile.sport === 'BADMINTON') &&
      (normalizedSearch.length === 0 ||
        player.displayName
          .toLocaleLowerCase('vi-VN')
          .includes(normalizedSearch)),
  )

  if (sessionStatus !== 'IN_PROGRESS') {
    return null
  }

  async function handleAddExisting() {
    const player = players.find((candidate) => candidate.id === selectedPlayerId)
    if (!player || action.isPending) {
      return
    }
    if (await action.addExistingPlayer(player)) {
      setSelectedPlayerId('')
      setSearch('')
      setOpen(false)
    }
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const name = displayName.trim()
    if (name.length === 0 || action.isPending || action.createOutcomeUnknown) {
      return
    }
    if (
      await action.createAndAddPlayer({
        displayName: name,
        sport: 'BADMINTON',
        skillLevel,
      })
    ) {
      setDisplayName('')
      setSearch('')
      setShowCreate(false)
      setOpen(false)
    }
  }

  return (
    <div className="live-addition">
      <button
        className="secondary-action-button"
        type="button"
        onClick={() => setOpen((current) => !current)}
      >
        + Thêm người chơi
      </button>
      {open && (
        <div className="live-addition-panel" aria-label="Thêm người chơi vào phiên">
          <label className="live-addition-field">
            <span>Tìm người chơi</span>
            <input
              type="search"
              value={search}
              placeholder="Nhập tên người chơi"
              disabled={action.isPending}
              onChange={(event) => {
                setSearch(event.target.value)
                setSelectedPlayerId('')
              }}
            />
          </label>
          <div className="live-candidate-list">
            {eligiblePlayers.map((player) => {
              const profile = player.sportProfiles.find(
                (candidate) => candidate.sport === 'BADMINTON',
              )
              return (
                <label className="live-candidate" key={player.id}>
                  <input
                    type="radio"
                    name="live-player"
                    value={player.id}
                    checked={selectedPlayerId === player.id}
                    disabled={action.isPending || action.hasUnknownAddOutcome}
                    onChange={() => setSelectedPlayerId(player.id)}
                  />
                  <span>{player.displayName}</span>
                  <small>{profile ? skillLevelLabel(profile.skillLevel) : '—'}</small>
                </label>
              )
            })}
            {eligiblePlayers.length === 0 && (
              <p className="empty-state">Không tìm thấy người chơi phù hợp.</p>
            )}
          </div>
          <div className="live-addition-actions">
            <button
              className="primary-action-button"
              type="button"
              disabled={
                selectedPlayerId.length === 0 ||
                action.isPending ||
                action.hasUnknownAddOutcome
              }
              onClick={() => void handleAddExisting()}
            >
              {action.isPending ? 'Đang thêm…' : 'Thêm vào phiên'}
            </button>
            <button
              className="secondary-action-button"
              type="button"
              disabled={action.isPending}
              onClick={() => {
                setShowCreate((current) => !current)
                setDisplayName(search.trim())
              }}
            >
              Tạo người chơi mới
            </button>
          </div>
          {showCreate && (
            <form className="live-create-player" onSubmit={handleCreate}>
              <label className="live-addition-field">
                <span>Tên người chơi</span>
                <input
                  value={displayName}
                  maxLength={120}
                  required
                  disabled={action.isPending || action.createOutcomeUnknown}
                  onChange={(event) => setDisplayName(event.target.value)}
                />
              </label>
              <label className="live-addition-field">
                <span>Trình độ</span>
                <select
                  value={skillLevel}
                  disabled={action.isPending || action.createOutcomeUnknown}
                  onChange={(event) =>
                    setSkillLevel(event.target.value as SkillLevel)
                  }
                >
                  {SKILL_LEVELS.map((level) => (
                    <option key={level} value={level}>
                      {skillLevelLabel(level)}
                    </option>
                  ))}
                </select>
              </label>
              <button
                className="primary-action-button"
                disabled={action.isPending || action.createOutcomeUnknown}
              >
                {action.isPending ? 'Đang tạo và thêm…' : 'Tạo và thêm vào phiên'}
              </button>
            </form>
          )}
          {action.recoveryPlayer && (
            <button
              className="secondary-action-button recovery-action"
              type="button"
              disabled={action.isPending || action.hasUnknownAddOutcome}
              onClick={async () => {
                if (await action.retryCreatedPlayer()) {
                  setOpen(false)
                }
              }}
            >
              Thử thêm {action.recoveryPlayer.displayName} vào phiên
            </button>
          )}
          {action.hasUnknownAddOutcome && (
            <button
              className="secondary-action-button recovery-action"
              type="button"
              disabled={action.isPending}
              onClick={async () => {
                if (await action.reconcileUnknown()) {
                  setOpen(false)
                }
              }}
            >
              Kiểm tra lại trạng thái người chơi
            </button>
          )}
          {action.message && (
            <p className="action-feedback" role="status">{action.message}</p>
          )}
        </div>
      )}
    </div>
  )
}

export function LiveAddCourt({
  sessionId,
  sessionStatus,
  venueId,
  venueCourts,
  sessionCourts,
}: {
  readonly sessionId: string
  readonly sessionStatus: SessionStatus
  readonly venueId: string
  readonly venueCourts: readonly CourtResponse[]
  readonly sessionCourts: readonly SessionCourtResponse[]
}) {
  const action = useLiveAddCourt(sessionId)
  const [open, setOpen] = useState(false)
  const [selectedCourtId, setSelectedCourtId] = useState('')
  const allocatedCourtIds = new Set(
    sessionCourts.map((sessionCourt) => sessionCourt.courtId),
  )
  const eligibleCourts = venueCourts.filter(
    (court) =>
      court.venueId === venueId &&
      court.active &&
      court.sport === 'BADMINTON' &&
      !allocatedCourtIds.has(court.id),
  )

  if (sessionStatus !== 'IN_PROGRESS' || venueId.length === 0) {
    return null
  }

  async function handleAddCourt() {
    const court = eligibleCourts.find((candidate) => candidate.id === selectedCourtId)
    if (!court || action.isPending) {
      return
    }
    if (await action.addCourt(court)) {
      setSelectedCourtId('')
      setOpen(false)
    }
  }

  return (
    <div className="live-addition court-addition">
      <button
        className="secondary-action-button"
        type="button"
        onClick={() => setOpen((current) => !current)}
      >
        + Thêm sân
      </button>
      {open && (
        <div className="live-addition-panel" aria-label="Thêm sân vào phiên">
          {eligibleCourts.length === 0 ? (
            <p className="empty-state">Không còn sân phù hợp để thêm.</p>
          ) : (
            <div className="live-candidate-list court-candidate-list">
              {eligibleCourts.map((court) => (
                <label className="live-candidate" key={court.id}>
                  <input
                    type="radio"
                    name="live-court"
                    value={court.id}
                    checked={selectedCourtId === court.id}
                    disabled={action.isPending || action.hasUnknownOutcome}
                    onChange={() => setSelectedCourtId(court.id)}
                  />
                  <span>{court.name}</span>
                </label>
              ))}
            </div>
          )}
          <button
            className="primary-action-button"
            type="button"
            disabled={
              selectedCourtId.length === 0 ||
              action.isPending ||
              action.hasUnknownOutcome
            }
            onClick={() => void handleAddCourt()}
          >
            {action.isPending ? 'Đang thêm sân…' : 'Thêm sân vào phiên'}
          </button>
          {action.hasUnknownOutcome && (
            <button
              className="secondary-action-button recovery-action"
              type="button"
              disabled={action.isPending}
              onClick={async () => {
                if (await action.reconcileUnknown()) {
                  setOpen(false)
                }
              }}
            >
              Kiểm tra lại trạng thái sân
            </button>
          )}
          {action.message && (
            <p className="action-feedback" role="status">{action.message}</p>
          )}
        </div>
      )}
    </div>
  )
}
