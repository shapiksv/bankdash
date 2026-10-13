/**
 * fetch wrapper for the gateway. Keeps the access token in memory only (never in storage), attaches it as a
 * Bearer token, and on a 401 refreshes it once through the httpOnly refresh cookie and retries.
 *
 * Refresh tokens rotate and a reused one revokes the whole login (see docs/adr/0001), so two refreshes must
 * never race with the same cookie: concurrent callers in a tab share one in-flight refresh, and tabs take
 * turns through a Web Lock. When the next tab gets the lock, the cookie it sends is already the rotated one.
 */

export const API_BASE = '/api'

/** RFC 9457 problem details, as every service returns them. */
export interface Problem {
  type?: string
  title?: string
  status?: number
  detail?: string
  errors?: { field: string; message: string }[]
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem

  constructor(status: number, problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }

  /** Field name → message, for showing validation errors next to form fields. */
  fieldErrors(): Record<string, string> {
    return Object.fromEntries((this.problem.errors ?? []).map((e) => [e.field, e.message]))
  }
}

let accessToken: string | null = null
let inFlightRefresh: Promise<string | null> | null = null
let sessionExpiredListener: (() => void) | null = null

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}

/** Called when a request got a 401 and the session could not be refreshed. */
export function onSessionExpired(listener: (() => void) | null): void {
  sessionExpiredListener = listener
}

export interface TokenResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
}

/** Returns a fresh access token, or null when there is no valid session (the cookie is missing or dead). */
export function refreshAccessToken(): Promise<string | null> {
  inFlightRefresh ??= withRefreshLock(async () => {
    const response = await fetch(`${API_BASE}/auth/refresh`, { method: 'POST', credentials: 'same-origin' })
    if (!response.ok) {
      accessToken = null
      return null
    }
    const body = (await response.json()) as TokenResponse
    accessToken = body.accessToken
    return accessToken
  }).finally(() => {
    inFlightRefresh = null
  })
  return inFlightRefresh
}

function withRefreshLock<T>(task: () => Promise<T>): Promise<T> {
  const locks = typeof navigator === 'undefined' ? undefined : navigator.locks
  return locks ? locks.request('bankdash-token-refresh', task) : task()
}

export interface ApiRequestInit extends RequestInit {
  /** Set to false for endpoints that do not take an access token (login, signup). Default true. */
  auth?: boolean
}

/** JSON request. Throws {@link ApiError} for any non-2xx response. */
export async function apiFetch<T>(path: string, init: ApiRequestInit = {}): Promise<T> {
  const response = await request(path, init)
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

/** Binary download, such as the avatar, with the same token handling as {@link apiFetch}. */
export async function apiBlob(path: string, init: ApiRequestInit = {}): Promise<Blob> {
  return (await request(path, init)).blob()
}

async function request(path: string, init: ApiRequestInit): Promise<Response> {
  const { auth = true, ...requestInit } = init
  let response = await send(path, requestInit, auth)

  if (response.status === 401 && auth) {
    const token = await refreshAccessToken()
    if (token) {
      response = await send(path, requestInit, auth)
    }
    if (response.status === 401) {
      accessToken = null
      sessionExpiredListener?.()
    }
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response))
  }
  return response
}

function send(path: string, init: RequestInit, auth: boolean): Promise<Response> {
  const headers = new Headers(init.headers)
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (auth && accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return fetch(`${API_BASE}${path}`, { ...init, headers, credentials: 'same-origin' })
}

async function readProblem(response: Response): Promise<Problem> {
  try {
    return (await response.json()) as Problem
  } catch {
    return { status: response.status, title: response.statusText }
  }
}
