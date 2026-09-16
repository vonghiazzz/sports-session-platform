import type { CreateSessionRequest } from '../../api/contracts'
import { VIETNAM_TIME_ZONE } from '../../lib/presentation'

interface LocalDateTimeParts {
  readonly year: number
  readonly month: number
  readonly day: number
  readonly hour: number
  readonly minute: number
}

export interface SessionSetupDraft {
  readonly title: string
  readonly date: string
  readonly startTime: string
  readonly endTime: string
  readonly venueId: string
  readonly courtIds: readonly string[]
  readonly playerIds: readonly string[]
}

export interface SessionSetupValidation {
  readonly errors: readonly string[]
  readonly request: CreateSessionRequest | null
}

const timezonePartsFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: VIETNAM_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
})

function parseLocalParts(
  dateValue: string,
  timeValue: string,
): LocalDateTimeParts | null {
  const dateMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateValue)
  const timeMatch = /^(\d{2}):(\d{2})$/.exec(timeValue)
  if (!dateMatch || !timeMatch) {
    return null
  }

  const parts = {
    year: Number(dateMatch[1]),
    month: Number(dateMatch[2]),
    day: Number(dateMatch[3]),
    hour: Number(timeMatch[1]),
    minute: Number(timeMatch[2]),
  }
  if (
    parts.year < 1000 ||
    parts.month < 1 ||
    parts.month > 12 ||
    parts.day < 1 ||
    parts.day > 31 ||
    parts.hour < 0 ||
    parts.hour > 23 ||
    parts.minute < 0 ||
    parts.minute > 59
  ) {
    return null
  }
  return parts
}

function partsAtInstant(value: Date): LocalDateTimeParts & { readonly second: number } {
  const values = Object.fromEntries(
    timezonePartsFormatter
      .formatToParts(value)
      .filter((part) => part.type !== 'literal')
      .map((part) => [part.type, Number(part.value)]),
  )
  return {
    year: values.year,
    month: values.month,
    day: values.day,
    hour: values.hour,
    minute: values.minute,
    second: values.second,
  }
}

function sameLocalParts(
  actual: LocalDateTimeParts,
  expected: LocalDateTimeParts,
): boolean {
  return (
    actual.year === expected.year &&
    actual.month === expected.month &&
    actual.day === expected.day &&
    actual.hour === expected.hour &&
    actual.minute === expected.minute
  )
}

export function vietnamLocalDateTimeToInstant(
  dateValue: string,
  timeValue: string,
): string | null {
  const requested = parseLocalParts(dateValue, timeValue)
  if (requested === null) {
    return null
  }

  const wallClockMilliseconds = Date.UTC(
    requested.year,
    requested.month - 1,
    requested.day,
    requested.hour,
    requested.minute,
  )
  const reference = new Date(wallClockMilliseconds)
  const referenceParts = partsAtInstant(reference)
  const timezoneOffsetMilliseconds =
    Date.UTC(
      referenceParts.year,
      referenceParts.month - 1,
      referenceParts.day,
      referenceParts.hour,
      referenceParts.minute,
      referenceParts.second,
    ) - wallClockMilliseconds
  const instant = new Date(wallClockMilliseconds - timezoneOffsetMilliseconds)

  if (!sameLocalParts(partsAtInstant(instant), requested)) {
    return null
  }
  return instant.toISOString()
}

export function validateSessionSetupDraft(
  draft: SessionSetupDraft,
): SessionSetupValidation {
  const errors: string[] = []
  const plannedStartAt = vietnamLocalDateTimeToInstant(
    draft.date,
    draft.startTime,
  )
  const plannedEndAt = vietnamLocalDateTimeToInstant(
    draft.date,
    draft.endTime,
  )

  if (draft.title.trim().length === 0) {
    errors.push('Hãy nhập tiêu đề phiên.')
  }
  if (plannedStartAt === null || plannedEndAt === null) {
    errors.push('Hãy nhập ngày và giờ hợp lệ theo giờ Việt Nam.')
  } else if (plannedEndAt <= plannedStartAt) {
    errors.push('Giờ kết thúc phải sau giờ bắt đầu.')
  }
  if (draft.venueId.length === 0) {
    errors.push('Hãy chọn địa điểm.')
  }
  if (draft.courtIds.length === 0) {
    errors.push('Hãy chọn ít nhất một sân.')
  }
  if (draft.playerIds.length === 0) {
    errors.push('Hãy chọn ít nhất một người chơi.')
  }

  if (
    errors.length > 0 ||
    plannedStartAt === null ||
    plannedEndAt === null
  ) {
    return { errors, request: null }
  }

  return {
    errors,
    request: {
      venueId: draft.venueId,
      title: draft.title.trim(),
      sport: 'BADMINTON',
      matchFormat: 'DOUBLES',
      plannedStartAt,
      plannedEndAt,
    },
  }
}

export function formatSetupLocalDateTime(
  dateValue: string,
  timeValue: string,
): string {
  const parts = parseLocalParts(dateValue, timeValue)
  if (parts === null) {
    return '—'
  }
  return `${String(parts.day).padStart(2, '0')}/${String(parts.month).padStart(2, '0')}/${parts.year} · ${String(parts.hour).padStart(2, '0')}:${String(parts.minute).padStart(2, '0')}`
}
