import { describe, expect, it } from 'vitest'
import { buildPlayerSessionUrl } from './playerSessionAccessUrl'

describe('buildPlayerSessionUrl', () => {
  it('builds one absolute browser URL with an encoded token segment', () => {
    expect(buildPlayerSessionUrl('token/with reserved?characters')).toBe(
      new URL(
        '/player-session/token%2Fwith%20reserved%3Fcharacters',
        window.location.origin,
      ).toString(),
    )
  })
})
