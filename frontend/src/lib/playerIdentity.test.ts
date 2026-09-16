import { describe, expect, it } from 'vitest'
import type { PlayerResponse } from '../api/contracts'
import {
  matchesPlayerIdentitySearch,
  playerIdentityLabel,
} from './playerIdentity'

const player: PlayerResponse = {
  id: 'player-123',
  playerCode: 'P000123',
  displayName: 'Nguyễn An',
  sportProfiles: [],
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
}

describe('Player identity presentation', () => {
  it('combines the global code and display name', () => {
    expect(playerIdentityLabel(player)).toBe('P000123 · Nguyễn An')
  })

  it.each(['P000123', 'p000123', '000123', 'nguyễn an', 'NGUYỄN'])(
    'matches a Player by name or code using %s',
    (search) => {
      expect(matchesPlayerIdentitySearch(player, search)).toBe(true)
    },
  )
})
