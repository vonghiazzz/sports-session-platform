import { describe, expect, it } from 'vitest'
import {
  COURT_ACTION_LABELS,
  formatVietnamDateTime,
  MATCH_ACTION_LABELS,
  matchFormatLabel,
  matchSourceLabel,
  PARTICIPANT_ACTION_LABELS,
  SESSION_ACTION_LABELS,
  skillLevelLabel,
  sportLabel,
  statusLabel,
} from './presentation'

describe('Vietnam presentation', () => {
  it.each([
    ['2026-09-04T11:00:00Z', '18:00 04/09/2026'],
    ['2026-09-04T13:00:00Z', '20:00 04/09/2026'],
    ['2026-09-04T20:30:00Z', '03:30 05/09/2026'],
  ])('formats %s in Asia/Ho_Chi_Minh as %s', (input, expected) => {
    expect(formatVietnamDateTime(input)).toBe(expected)
  })

  it.each([null, undefined, '', 'not-an-instant'])(
    'uses a safe fallback for %s',
    (input) => {
      expect(formatVietnamDateTime(input)).toBe('—')
    },
  )

  it('maps every current status to Vietnamese', () => {
    expect([
      statusLabel('PLANNED'),
      statusLabel('IN_PROGRESS'),
      statusLabel('COMPLETED'),
      statusLabel('CANCELLED'),
      statusLabel('REGISTERED'),
      statusLabel('WAITING'),
      statusLabel('PLAYING'),
      statusLabel('PAUSED'),
      statusLabel('LEFT'),
      statusLabel('AVAILABLE'),
      statusLabel('UNAVAILABLE'),
      statusLabel('CREATED'),
    ]).toEqual([
      'Đã lên lịch',
      'Đang diễn ra',
      'Đã kết thúc',
      'Đã hủy',
      'Đã đăng ký',
      'Đang chờ',
      'Đang chơi',
      'Tạm nghỉ',
      'Đã rời',
      'Sẵn sàng',
      'Tạm khóa',
      'Đã tạo',
    ])
  })

  it('maps current skills, sources, sport and format to Vietnamese', () => {
    expect([
      skillLevelLabel('WEAK'),
      skillLevelLabel('WEAK_PLUS'),
      skillLevelLabel('INTERMEDIATE_MINUS'),
      skillLevelLabel('INTERMEDIATE'),
      skillLevelLabel('INTERMEDIATE_PLUS'),
      skillLevelLabel('GOOD'),
    ]).toEqual(['Yếu', 'Yếu+', 'TB-', 'TB', 'TB+', 'Khá'])
    expect([
      matchSourceLabel('MANUAL'),
      matchSourceLabel('RECOMMENDATION'),
      matchSourceLabel('MODIFIED_RECOMMENDATION'),
    ]).toEqual(['Thủ công', 'Đề xuất', 'Đề xuất đã chỉnh sửa'])
    expect(sportLabel('BADMINTON')).toBe('Cầu lông')
    expect(matchFormatLabel('DOUBLES')).toBe('Đánh đôi')
  })

  it('falls back to an unexpected future value without throwing', () => {
    expect(statusLabel('FUTURE_STATUS')).toBe('FUTURE_STATUS')
    expect(skillLevelLabel('FUTURE_SKILL')).toBe('FUTURE_SKILL')
    expect(matchSourceLabel('FUTURE_SOURCE')).toBe('FUTURE_SOURCE')
    expect(sportLabel('FUTURE_SPORT')).toBe('FUTURE_SPORT')
    expect(matchFormatLabel('FUTURE_FORMAT')).toBe('FUTURE_FORMAT')
  })

  it('centralizes Vietnamese labels for every current Host action', () => {
    expect(PARTICIPANT_ACTION_LABELS).toEqual({
      CHECK_IN: { idle: 'Điểm danh', pending: 'Đang điểm danh…' },
      PAUSE: { idle: 'Tạm nghỉ', pending: 'Đang tạm nghỉ…' },
      RESUME: { idle: 'Trở lại', pending: 'Đang đưa trở lại…' },
      LEAVE: { idle: 'Rời phiên', pending: 'Đang rời phiên…' },
    })
    expect(COURT_ACTION_LABELS).toEqual({
      DISABLE: { idle: 'Tạm khóa sân', pending: 'Đang tạm khóa…' },
      ENABLE: { idle: 'Mở sân', pending: 'Đang mở sân…' },
    })
    expect(MATCH_ACTION_LABELS).toEqual({
      START: { idle: 'Bắt đầu trận', pending: 'Đang bắt đầu…' },
      COMPLETE: { idle: 'Kết thúc trận', pending: 'Đang kết thúc…' },
      CANCEL: { idle: 'Hủy trận', pending: 'Đang hủy…' },
    })
    expect(SESSION_ACTION_LABELS).toEqual({
      COMPLETE: { idle: 'Kết thúc phiên', pending: 'Đang kết thúc…' },
      CANCEL: { idle: 'Hủy phiên', pending: 'Đang hủy…' },
    })
  })
})
