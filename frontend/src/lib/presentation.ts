import type {
  MatchFormat,
  MatchSource,
  MatchStatus,
  ParticipantStatus,
  SessionCourtStatus,
  SessionStatus,
  SkillLevel,
  SportCode,
} from '../api/contracts'

export const VIETNAM_TIME_ZONE = 'Asia/Ho_Chi_Minh'

const vietnamDateTimeFormatter = new Intl.DateTimeFormat('vi-VN', {
  timeZone: VIETNAM_TIME_ZONE,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
})

const STATUS_LABELS: Readonly<Record<string, string>> = {
  PLANNED: 'Đã lên lịch',
  IN_PROGRESS: 'Đang diễn ra',
  COMPLETED: 'Đã kết thúc',
  CANCELLED: 'Đã hủy',
  REGISTERED: 'Đã đăng ký',
  WAITING: 'Đang chờ',
  PLAYING: 'Đang chơi',
  PAUSED: 'Tạm nghỉ',
  LEFT: 'Đã rời',
  AVAILABLE: 'Sẵn sàng',
  UNAVAILABLE: 'Tạm khóa',
  CREATED: 'Đã tạo',
}

const SKILL_LEVEL_LABELS: Readonly<Record<SkillLevel, string>> = {
  WEAK: 'Yếu',
  WEAK_PLUS: 'Yếu+',
  INTERMEDIATE_MINUS: 'TB-',
  INTERMEDIATE: 'TB',
  INTERMEDIATE_PLUS: 'TB+',
  GOOD: 'Khá',
}

const MATCH_SOURCE_LABELS: Readonly<Record<MatchSource, string>> = {
  MANUAL: 'Thủ công',
  RECOMMENDATION: 'Đề xuất',
  MODIFIED_RECOMMENDATION: 'Đề xuất đã chỉnh sửa',
}

const SPORT_LABELS: Readonly<Record<SportCode, string>> = {
  BADMINTON: 'Cầu lông',
}

const MATCH_FORMAT_LABELS: Readonly<Record<MatchFormat, string>> = {
  DOUBLES: 'Đánh đôi',
}

export interface ActionLabel {
  readonly idle: string
  readonly pending: string
}

export const PARTICIPANT_ACTION_LABELS: Readonly<
  Record<'CHECK_IN' | 'PAUSE' | 'RESUME' | 'LEAVE', ActionLabel>
> = {
  CHECK_IN: { idle: 'Điểm danh', pending: 'Đang điểm danh…' },
  PAUSE: { idle: 'Tạm nghỉ', pending: 'Đang tạm nghỉ…' },
  RESUME: { idle: 'Trở lại', pending: 'Đang đưa trở lại…' },
  LEAVE: { idle: 'Rời phiên', pending: 'Đang rời phiên…' },
}

export const COURT_ACTION_LABELS: Readonly<
  Record<'DISABLE' | 'ENABLE', ActionLabel>
> = {
  DISABLE: { idle: 'Tạm khóa sân', pending: 'Đang tạm khóa…' },
  ENABLE: { idle: 'Mở sân', pending: 'Đang mở sân…' },
}

export const MATCH_ACTION_LABELS: Readonly<
  Record<'START' | 'COMPLETE' | 'CANCEL', ActionLabel>
> = {
  START: { idle: 'Bắt đầu trận', pending: 'Đang bắt đầu…' },
  COMPLETE: { idle: 'Kết thúc trận', pending: 'Đang kết thúc…' },
  CANCEL: { idle: 'Hủy trận', pending: 'Đang hủy…' },
}

export const SESSION_ACTION_LABELS: Readonly<
  Record<'COMPLETE' | 'CANCEL', ActionLabel>
> = {
  COMPLETE: { idle: 'Kết thúc phiên', pending: 'Đang kết thúc…' },
  CANCEL: { idle: 'Hủy phiên', pending: 'Đang hủy…' },
}

function mappedLabel(
  labels: Readonly<Record<string, string>>,
  value: string,
): string {
  return labels[value] ?? value
}

export function formatVietnamDateTime(
  value: string | null | undefined,
): string {
  if (value === null || value === undefined) {
    return '—'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? '—'
    : vietnamDateTimeFormatter.format(date)
}

export function statusLabel(
  value:
    | SessionStatus
    | ParticipantStatus
    | SessionCourtStatus
    | MatchStatus
    | string,
): string {
  return mappedLabel(STATUS_LABELS, value)
}

export function skillLevelLabel(value: SkillLevel | string): string {
  return mappedLabel(SKILL_LEVEL_LABELS, value)
}

export function matchSourceLabel(value: MatchSource | string): string {
  return mappedLabel(MATCH_SOURCE_LABELS, value)
}

export function sportLabel(value: SportCode | string): string {
  return mappedLabel(SPORT_LABELS, value)
}

export function matchFormatLabel(value: MatchFormat | string): string {
  return mappedLabel(MATCH_FORMAT_LABELS, value)
}
