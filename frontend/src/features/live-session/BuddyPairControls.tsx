import { useMemo, useState } from 'react'
import type { SessionStatus } from '../../api/contracts'
import { statusLabel } from '../../lib/presentation'
import type { ParticipantView } from './liveSessionModel'
import type { BuddyPairActions } from './useBuddyPairActions'

function isMutableSession(status: SessionStatus): boolean {
  return status === 'PLANNED' || status === 'IN_PROGRESS'
}

function buddyPartnerName(
  participant: ParticipantView,
  participants: readonly ParticipantView[],
): string | null {
  if (participant.buddyPairId === null) {
    return null
  }
  const members = participants.filter(
    (candidate) => candidate.buddyPairId === participant.buddyPairId,
  )
  if (members.length !== 2) {
    return null
  }
  return (
    members.find(
      (candidate) =>
        candidate.sessionParticipantId !== participant.sessionParticipantId,
    )?.displayName ?? null
  )
}

export function BuddyPairControls({
  participant,
  participants,
  sessionStatus,
  actions,
}: {
  readonly participant: ParticipantView
  readonly participants: readonly ParticipantView[]
  readonly sessionStatus: SessionStatus
  readonly actions: BuddyPairActions
}) {
  const [open, setOpen] = useState(false)
  const [selectedParticipantId, setSelectedParticipantId] = useState('')
  const mutable = isMutableSession(sessionStatus)
  const partnerName = buddyPartnerName(participant, participants)
  const eligibleParticipants = useMemo(
    () =>
      participants.filter(
        (candidate) =>
          candidate.sessionParticipantId !== participant.sessionParticipantId &&
          candidate.buddyPairId === null,
      ),
    [participant.sessionParticipantId, participants],
  )
  const selectedParticipant = eligibleParticipants.find(
    (candidate) =>
      candidate.sessionParticipantId === selectedParticipantId,
  )
  const createPending = actions.isCreatePending(
    participant.sessionParticipantId,
  )

  if (participant.buddyPairId !== null) {
    const buddyPairId = participant.buddyPairId
    const removePending = actions.isRemovePending(buddyPairId)
    const removeError = actions.removeError(buddyPairId)
    return (
      <div className="buddy-pair-controls">
        <span className="buddy-pair-label">
          {partnerName === null
            ? 'Đã ghép bạn'
            : `Đánh cùng: ${partnerName}`}
        </span>
        {mutable && (
          <button
            className="secondary-action-button buddy-remove-button"
            type="button"
            disabled={removePending}
            onClick={() => void actions.remove(buddyPairId)}
          >
            {removePending ? 'Đang hủy…' : 'Hủy đánh cùng'}
          </button>
        )}
        {removeError && (
          <span className="action-feedback" role="alert">
            {removeError}
          </span>
        )}
      </div>
    )
  }

  if (!mutable) {
    return null
  }

  const createError = actions.createError(participant.sessionParticipantId)

  return (
    <div className="buddy-pair-controls">
      <button
        className="secondary-action-button"
        type="button"
        disabled={createPending}
        onClick={() => setOpen((current) => !current)}
      >
        Đánh cùng bạn
      </button>
      {open && (
        <div
          className="buddy-pair-selector"
          aria-label={`Chọn bạn đánh cùng cho ${participant.displayName}`}
        >
          <strong>{participant.displayName}</strong>
          <span>chọn một người chơi chưa ghép bạn:</span>
          {eligibleParticipants.length === 0 ? (
            <span className="empty-state">Không còn người chơi phù hợp.</span>
          ) : (
            <div className="buddy-candidate-list">
              {eligibleParticipants.map((candidate) => (
                <label className="live-candidate" key={candidate.sessionParticipantId}>
                  <input
                    type="radio"
                    name={`buddy-for-${participant.sessionParticipantId}`}
                    value={candidate.sessionParticipantId}
                    aria-label={`Chọn ${candidate.displayName} (${candidate.sessionParticipantId})`}
                    checked={
                      selectedParticipantId === candidate.sessionParticipantId
                    }
                    disabled={createPending}
                    onChange={() =>
                      setSelectedParticipantId(candidate.sessionParticipantId)
                    }
                  />
                  <span>{candidate.displayName}</span>
                  <small>
                    {candidate.skillLabel ?? '—'} · {statusLabel(candidate.status)} ·{' '}
                    {candidate.sessionParticipantId.slice(0, 8)}
                  </small>
                </label>
              ))}
            </div>
          )}
          {selectedParticipant && (
            <p className="buddy-pair-confirmation">
              Ghép {participant.displayName} với {selectedParticipant.displayName}?
            </p>
          )}
          <div className="action-area buddy-pair-actions">
            <button
              className="primary-action-button"
              type="button"
              disabled={selectedParticipant === undefined || createPending}
              onClick={async () => {
                if (
                  selectedParticipant &&
                  (await actions.create(
                    participant.sessionParticipantId,
                    selectedParticipant.sessionParticipantId,
                  ))
                ) {
                  setOpen(false)
                  setSelectedParticipantId('')
                }
              }}
            >
              {createPending ? 'Đang ghép…' : 'Xác nhận đánh cùng'}
            </button>
            <button
              className="secondary-action-button"
              type="button"
              disabled={createPending}
              onClick={() => {
                setOpen(false)
                setSelectedParticipantId('')
              }}
            >
              Đóng
            </button>
          </div>
        </div>
      )}
      {createError && (
        <span className="action-feedback" role="alert">
          {createError}
        </span>
      )}
    </div>
  )
}
