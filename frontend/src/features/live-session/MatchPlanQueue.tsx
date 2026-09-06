import { useMemo, useState, type FormEvent } from 'react'
import type { MatchPlanResponse, SaveMatchPlanRequest } from '../../api/contracts'
import type { CourtView, ParticipantView } from './liveSessionModel'
import {
  useCreateMatchPlan,
  useMatchPlanActions,
} from './useMatchPlanActions'
import { matchPlanStartReason } from './matchPlanReadiness'

type PlanSlot = 'A1' | 'A2' | 'B1' | 'B2'

const PLAN_SLOTS: readonly {
  readonly id: PlanSlot
  readonly label: string
  readonly teamSide: 'A' | 'B'
  readonly teamSlot: 1 | 2
}[] = [
  { id: 'A1', label: 'Đội A — Vị trí 1', teamSide: 'A', teamSlot: 1 },
  { id: 'A2', label: 'Đội A — Vị trí 2', teamSide: 'A', teamSlot: 2 },
  { id: 'B1', label: 'Đội B — Vị trí 1', teamSide: 'B', teamSlot: 1 },
  { id: 'B2', label: 'Đội B — Vị trí 2', teamSide: 'B', teamSlot: 2 },
]

const EMPTY_PLAN_SLOTS: Readonly<Record<PlanSlot, string>> = {
  A1: '',
  A2: '',
  B1: '',
  B2: '',
}

function slotsFor(plan?: MatchPlanResponse): Readonly<Record<PlanSlot, string>> {
  if (!plan) {
    return EMPTY_PLAN_SLOTS
  }
  return Object.fromEntries(
    PLAN_SLOTS.map((slot) => [
      slot.id,
      plan.participants.find(
        (assignment) =>
          assignment.teamSide === slot.teamSide &&
          assignment.teamSlot === slot.teamSlot,
      )?.sessionParticipantId ?? '',
    ]),
  ) as unknown as Readonly<Record<PlanSlot, string>>
}

function planRequest(
  participantsBySlot: Readonly<Record<PlanSlot, string>>,
): SaveMatchPlanRequest {
  return {
    participants: PLAN_SLOTS.map((slot) => ({
      sessionParticipantId: participantsBySlot[slot.id],
      teamSide: slot.teamSide,
      teamSlot: slot.teamSlot,
    })),
  }
}

function planningOptionLabel(participant: ParticipantView): string {
  const statusContext =
    participant.status === 'PLAYING'
      ? 'Đang chơi'
      : participant.status === 'PAUSED'
        ? 'Đang tạm nghỉ'
        : participant.status === 'REGISTERED'
          ? 'Chưa điểm danh'
          : 'Đang chờ'
  const planContext =
    participant.plannedMatchCount > 0
      ? ` · Đã được xếp ${participant.plannedMatchCount} trận chờ khác`
      : ''
  return `${participant.displayName} · ${participant.skillLabel ?? 'Chưa có trình độ'} · ${statusContext}${planContext}`
}

function MatchPlanEditor({
  participants,
  initialPlan,
  pending,
  submitLabel,
  onSubmit,
  onClose,
}: {
  readonly participants: readonly ParticipantView[]
  readonly initialPlan?: MatchPlanResponse
  readonly pending: boolean
  readonly submitLabel: string
  readonly onSubmit: (request: SaveMatchPlanRequest) => Promise<boolean>
  readonly onClose: () => void
}) {
  const candidates = participants.filter(
    (participant) => participant.status !== 'LEFT',
  )
  const candidateIds = new Set(
    candidates.map((participant) => participant.sessionParticipantId),
  )
  const [participantsBySlot, setParticipantsBySlot] = useState(() =>
    slotsFor(initialPlan),
  )
  const selected = Object.values(participantsBySlot).filter(Boolean)
  const unique = new Set(selected).size === selected.length
  const valid =
    selected.length === 4 &&
    unique &&
    selected.every((participantId) => candidateIds.has(participantId))

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!valid || pending) {
      return
    }
    if (await onSubmit(planRequest(participantsBySlot))) {
      onClose()
    }
  }

  return (
    <form className="match-plan-editor" onSubmit={(event) => void handleSubmit(event)}>
      <div className="match-plan-team-fields">
        {PLAN_SLOTS.map((slot) => (
          <label className="match-field" key={slot.id}>
            <span>{slot.label}</span>
            <select
              aria-label={slot.label}
              value={
                candidateIds.has(participantsBySlot[slot.id])
                  ? participantsBySlot[slot.id]
                  : ''
              }
              disabled={pending}
              onChange={(event) =>
                setParticipantsBySlot((current) => ({
                  ...current,
                  [slot.id]: event.target.value,
                }))
              }
            >
              <option value="">Chọn người chơi</option>
              {candidates.map((participant) => {
                const selectedElsewhere = PLAN_SLOTS.some(
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
                    {planningOptionLabel(participant)}
                  </option>
                )
              })}
            </select>
          </label>
        ))}
      </div>
      {!unique && (
        <p className="form-note" role="alert">
          Mỗi người chơi chỉ được chọn một lần trong trận chờ này.
        </p>
      )}
      <p className="form-note">
        Xếp trận không giữ sân hoặc người chơi. Trạng thái sẽ được kiểm tra lại
        khi bắt đầu.
      </p>
      <div className="action-area">
        <button className="primary-action-button" type="submit" disabled={!valid || pending}>
          {pending ? 'Đang lưu…' : submitLabel}
        </button>
        <button className="secondary-action-button" type="button" disabled={pending} onClick={onClose}>
          Đóng
        </button>
      </div>
    </form>
  )
}

function teamNames(
  plan: MatchPlanResponse,
  side: 'A' | 'B',
  participantById: ReadonlyMap<string, ParticipantView>,
): string {
  return plan.participants
    .filter((assignment) => assignment.teamSide === side)
    .toSorted((left, right) => left.teamSlot - right.teamSlot)
    .map(
      (assignment) =>
        participantById.get(assignment.sessionParticipantId)?.displayName ??
        'Không có dữ liệu',
    )
    .join(' + ')
}

function QueuedPlanCard({
  plan,
  index,
  queueLength,
  court,
  courts,
  participants,
  sessionId,
  sessionStatus,
}: {
  readonly plan: MatchPlanResponse
  readonly index: number
  readonly queueLength: number
  readonly court: CourtView
  readonly courts: readonly CourtView[]
  readonly participants: readonly ParticipantView[]
  readonly sessionId: string
  readonly sessionStatus: string
}) {
  const action = useMatchPlanActions(sessionId, plan.id)
  const [editing, setEditing] = useState(false)
  const [moving, setMoving] = useState(false)
  const [targetCourtId, setTargetCourtId] = useState('')
  const [confirmingCancel, setConfirmingCancel] = useState(false)
  const participantById = useMemo(
    () =>
      new Map(
        participants.map((participant) => [
          participant.sessionParticipantId,
          participant,
        ]),
      ),
    [participants],
  )
  const startReason = matchPlanStartReason(
    plan,
    court,
    sessionStatus,
    participantById,
  )
  const mutable = sessionStatus === 'IN_PROGRESS'
  const otherCourts = courts.filter(
    (candidate) => candidate.sessionCourtId !== court.sessionCourtId,
  )

  return (
    <article className="match-plan-card" aria-label={`Trận tiếp theo số ${plan.queuePosition ?? index + 1}`}>
      <div className="match-plan-summary">
        <strong>#{plan.queuePosition ?? index + 1}</strong>
        <div>
          <span className="team-label">Đội A</span>
          <p>{teamNames(plan, 'A', participantById)}</p>
        </div>
        <span className="versus">đấu</span>
        <div>
          <span className="team-label">Đội B</span>
          <p>{teamNames(plan, 'B', participantById)}</p>
        </div>
      </div>

      {mutable && !editing && !moving && !confirmingCancel && (
        <div className="match-plan-actions">
          {plan.queuePosition === 1 && (
            <button
              className="primary-action-button"
              type="button"
              disabled={action.isPending || startReason !== null || action.hasUnknownOutcome}
              onClick={() => void action.execute({ type: 'START' })}
            >
              {action.pendingAction === 'START' ? 'Đang bắt đầu…' : 'Bắt đầu trận'}
            </button>
          )}
          <button className="secondary-action-button" type="button" disabled={action.isPending || action.hasUnknownOutcome} onClick={() => setEditing(true)}>
            Chỉnh
          </button>
          <button className="secondary-action-button" type="button" disabled={action.isPending || action.hasUnknownOutcome || otherCourts.length === 0} onClick={() => setMoving(true)}>
            Đổi sân
          </button>
          <button
            className="secondary-action-button"
            type="button"
            aria-label={`Đưa trận #${plan.queuePosition ?? index + 1} lên`}
            disabled={action.isPending || action.hasUnknownOutcome || index === 0}
            onClick={() => void action.execute({ type: 'REORDER', targetPosition: index })}
          >
            ↑
          </button>
          <button
            className="secondary-action-button"
            type="button"
            aria-label={`Đưa trận #${plan.queuePosition ?? index + 1} xuống`}
            disabled={action.isPending || action.hasUnknownOutcome || index === queueLength - 1}
            onClick={() => void action.execute({ type: 'REORDER', targetPosition: index + 2 })}
          >
            ↓
          </button>
          <button className="danger-action-button" type="button" disabled={action.isPending || action.hasUnknownOutcome} onClick={() => setConfirmingCancel(true)}>
            Hủy khỏi hàng chờ
          </button>
        </div>
      )}

      {startReason && <p className="match-plan-readiness">{startReason}</p>}
      {!startReason && plan.queuePosition === 1 && (
        <p className="match-plan-ready">Sẵn sàng bắt đầu</p>
      )}

      {editing && (
        <MatchPlanEditor
          participants={participants}
          initialPlan={plan}
          pending={action.isPending}
          submitLabel="Lưu thay đổi"
          onSubmit={(request) => action.execute({ type: 'EDIT', request })}
          onClose={() => setEditing(false)}
        />
      )}

      {moving && (
        <div className="match-plan-move-form">
          <label className="match-field">
            <span>Chuyển sang sân</span>
            <select value={targetCourtId} disabled={action.isPending} onChange={(event) => setTargetCourtId(event.target.value)}>
              <option value="">Chọn sân đích</option>
              {otherCourts.map((candidate) => (
                <option key={candidate.sessionCourtId} value={candidate.sessionCourtId}>
                  {candidate.name} · {candidate.status === 'AVAILABLE' ? 'Sẵn sàng' : candidate.status === 'PLAYING' ? 'Đang chơi' : 'Tạm khóa'}
                </option>
              ))}
            </select>
          </label>
          <div className="action-area">
            <button className="primary-action-button" type="button" disabled={!targetCourtId || action.isPending} onClick={async () => {
              if (await action.execute({ type: 'MOVE', targetSessionCourtId: targetCourtId })) {
                setMoving(false)
              }
            }}>
              {action.pendingAction === 'MOVE' ? 'Đang chuyển…' : 'Chuyển sân'}
            </button>
            <button className="secondary-action-button" type="button" disabled={action.isPending} onClick={() => setMoving(false)}>Đóng</button>
          </div>
        </div>
      )}

      {confirmingCancel && (
        <div className="cancel-confirmation">
          <p>Hủy trận này khỏi hàng chờ?</p>
          <div className="action-area">
            <button className="danger-action-button" type="button" disabled={action.isPending} onClick={async () => {
              if (await action.execute({ type: 'CANCEL' })) {
                setConfirmingCancel(false)
              }
            }}>
              {action.pendingAction === 'CANCEL' ? 'Đang hủy…' : 'Xác nhận hủy'}
            </button>
            <button className="secondary-action-button" type="button" disabled={action.isPending} onClick={() => setConfirmingCancel(false)}>Giữ trong hàng chờ</button>
          </div>
        </div>
      )}

      {action.errorMessage && <p className="action-feedback" role="alert">{action.errorMessage}</p>}
      {action.hasUnknownOutcome && (
        <button className="secondary-action-button" type="button" disabled={action.isPending} onClick={() => void action.reconcileUnknown()}>
          Kiểm tra lại
        </button>
      )}
    </article>
  )
}

export function MatchPlanQueue({
  sessionId,
  sessionStatus,
  court,
  courts,
  participants,
  matchPlans,
}: {
  readonly sessionId: string
  readonly sessionStatus: string
  readonly court: CourtView
  readonly courts: readonly CourtView[]
  readonly participants: readonly ParticipantView[]
  readonly matchPlans: readonly MatchPlanResponse[]
}) {
  const [creating, setCreating] = useState(false)
  const createAction = useCreateMatchPlan(sessionId, court.sessionCourtId)
  const queue = matchPlans
    .filter(
      (plan) =>
        plan.status === 'QUEUED' &&
        plan.sessionCourtId === court.sessionCourtId,
    )
    .toSorted(
      (left, right) =>
        (left.queuePosition ?? Number.MAX_SAFE_INTEGER) -
        (right.queuePosition ?? Number.MAX_SAFE_INTEGER),
    )
  const allParticipants = participants.filter(
    (participant) => participant.status !== 'LEFT',
  )

  return (
    <section className="match-plan-queue" aria-label={`Hàng chờ trận ${court.name}`}>
      <div className="match-plan-queue-heading">
        <div>
          <p className="eyebrow">Tiếp theo</p>
          <h4>Hàng chờ trận</h4>
        </div>
        <span>{queue.length}</span>
      </div>
      {court.status === 'UNAVAILABLE' && queue.length > 0 && (
        <p className="match-plan-court-warning">⚠ Sân đang tạm khóa</p>
      )}
      {queue.length === 0 ? (
        <p className="match-plan-empty">Chưa có trận nào được xếp tiếp theo.</p>
      ) : (
        <div className="match-plan-list">
          {queue.map((plan, index) => (
            <QueuedPlanCard
              key={plan.id}
              plan={plan}
              index={index}
              queueLength={queue.length}
              court={court}
              courts={courts}
              participants={participants}
              sessionId={sessionId}
              sessionStatus={sessionStatus}
            />
          ))}
        </div>
      )}
      {sessionStatus === 'IN_PROGRESS' && !creating && (
        <button
          className="secondary-action-button match-plan-create-button"
          type="button"
          disabled={createAction.isPending || createAction.hasUnknownOutcome}
          onClick={() => setCreating(true)}
        >
          Xếp trận tiếp theo
        </button>
      )}
      {creating && (
        <MatchPlanEditor
          participants={allParticipants}
          pending={createAction.isPending}
          submitLabel="Xếp trận"
          onSubmit={createAction.execute}
          onClose={() => setCreating(false)}
        />
      )}
      {createAction.errorMessage && <p className="action-feedback" role="alert">{createAction.errorMessage}</p>}
      {createAction.hasUnknownOutcome && (
        <button className="secondary-action-button" type="button" disabled={createAction.isPending} onClick={() => void createAction.reconcileUnknown()}>
          Kiểm tra lại
        </button>
      )}
    </section>
  )
}
