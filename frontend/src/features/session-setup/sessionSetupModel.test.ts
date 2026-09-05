import { describe, expect, it } from 'vitest'
import {
  validateSessionSetupDraft,
  vietnamLocalDateTimeToInstant,
} from './sessionSetupModel'

describe('Vietnam Session Setup time', () => {
  it('converts 18:00 Vietnam time to the correct Instant', () => {
    expect(vietnamLocalDateTimeToInstant('2026-09-05', '18:00')).toBe(
      '2026-09-05T11:00:00.000Z',
    )
  })

  it('converts 20:00 Vietnam time to the correct Instant', () => {
    expect(vietnamLocalDateTimeToInstant('2026-09-05', '20:00')).toBe(
      '2026-09-05T13:00:00.000Z',
    )
  })

  it('handles a Vietnam date boundary', () => {
    expect(vietnamLocalDateTimeToInstant('2026-09-05', '01:30')).toBe(
      '2026-09-04T18:30:00.000Z',
    )
  })

  it('rejects an end time that is not after the start time', () => {
    const result = validateSessionSetupDraft({
      title: 'Cầu lông tối thứ Bảy',
      date: '2026-09-05',
      startTime: '20:00',
      endTime: '18:00',
      venueId: 'venue-1',
      courtIds: ['court-1'],
      playerIds: ['player-1'],
    })

    expect(result.request).toBeNull()
    expect(result.errors).toContain('Giờ kết thúc phải sau giờ bắt đầu.')
  })

  it('builds the exact BADMINTON DOUBLES backend request', () => {
    const result = validateSessionSetupDraft({
      title: '  Cầu lông tối thứ Bảy  ',
      date: '2026-09-05',
      startTime: '18:00',
      endTime: '20:00',
      venueId: 'venue-1',
      courtIds: ['court-1'],
      playerIds: ['player-1'],
    })

    expect(result).toEqual({
      errors: [],
      request: {
        venueId: 'venue-1',
        title: 'Cầu lông tối thứ Bảy',
        sport: 'BADMINTON',
        matchFormat: 'DOUBLES',
        plannedStartAt: '2026-09-05T11:00:00.000Z',
        plannedEndAt: '2026-09-05T13:00:00.000Z',
      },
    })
  })
})
