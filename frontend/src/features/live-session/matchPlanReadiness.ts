import type { MatchPlanResponse } from '../../api/contracts'
import type { CourtView, ParticipantView } from './liveSessionModel'

export function matchPlanStartReason(
  plan: MatchPlanResponse,
  court: CourtView,
  sessionStatus: string,
  participantById: ReadonlyMap<string, ParticipantView>,
): string | null {
  if (sessionStatus !== 'IN_PROGRESS') {
    return 'Phiên không còn diễn ra'
  }
  if (plan.queuePosition !== 1) {
    return 'Chờ đến lượt'
  }
  if (court.status === 'PLAYING') {
    return 'Đang chờ sân trống'
  }
  if (court.status === 'UNAVAILABLE') {
    return 'Sân đang tạm khóa'
  }
  const statuses = plan.participants.map(
    (assignment) => participantById.get(assignment.sessionParticipantId)?.status,
  )
  if (statuses.some((status) => status === undefined)) {
    return 'Thiếu dữ liệu người chơi'
  }
  if (statuses.some((status) => status === 'PLAYING')) {
    return 'Có người chơi đang chơi'
  }
  if (statuses.some((status) => status === 'PAUSED')) {
    return 'Có người chơi đang tạm nghỉ'
  }
  if (statuses.some((status) => status === 'REGISTERED')) {
    return 'Có người chơi chưa điểm danh'
  }
  if (statuses.some((status) => status !== 'WAITING')) {
    return 'Người chơi chưa sẵn sàng'
  }
  return null
}
