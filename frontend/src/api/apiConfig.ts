export function normalizeApiBaseUrl(value: string | undefined): string {
  return (value ?? '').trim().replace(/\/+$/, '')
}

export function joinApiUrl(baseUrl: string, path: string): string {
  if (!path.startsWith('/')) {
    throw new Error('API path must start with /')
  }
  return `${normalizeApiBaseUrl(baseUrl)}${path}`
}

export const API_BASE_URL = normalizeApiBaseUrl(
  import.meta.env.VITE_API_BASE_URL,
)

export function apiUrl(path: string): string {
  return joinApiUrl(API_BASE_URL, path)
}
