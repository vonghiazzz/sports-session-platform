import type { ApiError } from './contracts'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isStringRecord(value: unknown): value is Record<string, string> {
  return (
    isRecord(value) &&
    Object.values(value).every((entry) => typeof entry === 'string')
  )
}

function isApiError(value: unknown): value is ApiError {
  return (
    isRecord(value) &&
    typeof value.timestamp === 'string' &&
    typeof value.status === 'number' &&
    typeof value.error === 'string' &&
    typeof value.message === 'string' &&
    typeof value.path === 'string' &&
    isStringRecord(value.fieldErrors)
  )
}

async function parseApiError(response: Response): Promise<ApiError | undefined> {
  try {
    const body: unknown = await response.json()
    return isApiError(body) ? body : undefined
  } catch {
    return undefined
  }
}

export class HttpError extends Error {
  readonly status: number
  readonly apiError?: ApiError

  constructor(status: number, message: string, apiError?: ApiError) {
    super(message)
    this.name = 'HttpError'
    this.status = status
    this.apiError = apiError
  }
}

async function requestJson<T>(
  path: string,
  method: 'GET' | 'POST',
  signal?: AbortSignal,
  body?: unknown,
): Promise<T> {
  const hasBody = body !== undefined
  const response = await fetch(path, {
    method,
    headers: {
      Accept: 'application/json',
      ...(hasBody ? { 'Content-Type': 'application/json' } : {}),
    },
    signal,
    ...(hasBody ? { body: JSON.stringify(body) } : {}),
  })

  if (!response.ok) {
    const apiError = await parseApiError(response)
    throw new HttpError(
      response.status,
      apiError?.message ?? `Request failed with status ${response.status}`,
      apiError,
    )
  }

  return (await response.json()) as T
}

export function getJson<T>(
  path: string,
  signal?: AbortSignal,
): Promise<T> {
  return requestJson(path, 'GET', signal)
}

export function postJson<T>(
  path: string,
  signal?: AbortSignal,
): Promise<T> {
  return requestJson(path, 'POST', signal)
}

export function postJsonWithBody<TResponse, TBody>(
  path: string,
  body: TBody,
  signal?: AbortSignal,
): Promise<TResponse> {
  return requestJson(path, 'POST', signal, body)
}
