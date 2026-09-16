import { describe, expect, it } from 'vitest'
import { joinApiUrl, normalizeApiBaseUrl } from './apiConfig'

describe('API configuration', () => {
  it('keeps relative API paths for the local Vite proxy', () => {
    expect(joinApiUrl('', '/api/health')).toBe('/api/health')
  })

  it('joins a production backend origin without a duplicate slash', () => {
    expect(
      joinApiUrl('https://sports-session-api.onrender.com/', '/api/health'),
    ).toBe('https://sports-session-api.onrender.com/api/health')
  })

  it('normalizes whitespace and trailing slashes', () => {
    expect(normalizeApiBaseUrl('  https://api.example.com/// ')).toBe(
      'https://api.example.com',
    )
  })

  it('rejects non-absolute API paths', () => {
    expect(() => joinApiUrl('https://api.example.com', 'api/health')).toThrow(
      'API path must start with /',
    )
  })
})
