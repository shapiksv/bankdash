import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { json, mockFetch, problem } from '../test/fetchMock'
import { ApiError, apiFetch, getAccessToken, onSessionExpired, refreshAccessToken, setAccessToken } from './client'

const tokens = (accessToken: string) => ({ accessToken, tokenType: 'Bearer', expiresIn: 900 })

describe('apiFetch', () => {
  beforeEach(() => setAccessToken(null))
  afterEach(() => {
    vi.unstubAllGlobals()
    onSessionExpired(null)
  })

  it('sends the access token as a Bearer header to /api', async () => {
    setAccessToken('t1')
    const { calls } = mockFetch({ 'GET /api/auth/me': () => json({ id: '1' }) })

    await expect(apiFetch('/auth/me')).resolves.toEqual({ id: '1' })
    expect(calls[0].headers.get('Authorization')).toBe('Bearer t1')
    expect(calls[0].credentials).toBe('same-origin')
  })

  it('does not send a token when auth is false', async () => {
    setAccessToken('t1')
    const { calls } = mockFetch({ 'POST /api/auth/login': () => json(tokens('t2')) })

    await apiFetch('/auth/login', { method: 'POST', auth: false, body: '{}' })

    expect(calls[0].headers.get('Authorization')).toBeNull()
    expect(calls[0].headers.get('Content-Type')).toBe('application/json')
  })

  it('refreshes on 401 and retries once with the new token', async () => {
    setAccessToken('expired')
    const { calls } = mockFetch({
      'GET /api/auth/me': (request) =>
        request.headers.get('Authorization') === 'Bearer fresh' ? json({ id: '1' }) : problem(401),
      'POST /api/auth/refresh': () => json(tokens('fresh')),
    })

    await expect(apiFetch('/auth/me')).resolves.toEqual({ id: '1' })
    expect(calls.map((c) => new URL(c.url).pathname)).toEqual(['/api/auth/me', '/api/auth/refresh', '/api/auth/me'])
    expect(getAccessToken()).toBe('fresh')
  })

  it('shares one refresh between concurrent 401s', async () => {
    setAccessToken('expired')
    const { calls } = mockFetch({
      'GET /api/a': (r) => (r.headers.get('Authorization') === 'Bearer fresh' ? json('a') : problem(401)),
      'GET /api/b': (r) => (r.headers.get('Authorization') === 'Bearer fresh' ? json('b') : problem(401)),
      'POST /api/auth/refresh': () => json(tokens('fresh')),
    })

    await expect(Promise.all([apiFetch('/a'), apiFetch('/b')])).resolves.toEqual(['a', 'b'])
    expect(calls.filter((c) => c.url.endsWith('/refresh'))).toHaveLength(1)
  })

  it('gives up when the refresh fails and reports the expired session', async () => {
    setAccessToken('expired')
    const expired = vi.fn()
    onSessionExpired(expired)
    const { calls } = mockFetch({
      'GET /api/auth/me': () => problem(401),
      'POST /api/auth/refresh': () => problem(401, { title: 'Session expired' }),
    })

    await expect(apiFetch('/auth/me')).rejects.toMatchObject({ status: 401 })
    expect(calls).toHaveLength(2)
    expect(expired).toHaveBeenCalledOnce()
    expect(getAccessToken()).toBeNull()
  })

  it('does not refresh for unauthenticated requests', async () => {
    const { calls } = mockFetch({
      'POST /api/auth/login': () => problem(401, { detail: 'Invalid email or password' }),
    })

    await expect(apiFetch('/auth/login', { method: 'POST', auth: false, body: '{}' })).rejects.toThrow(
      'Invalid email or password',
    )
    expect(calls).toHaveLength(1)
  })

  it('turns ProblemDetail field errors into a map', async () => {
    mockFetch({
      'POST /api/auth/signup': () =>
        problem(400, { title: 'Invalid request', errors: [{ field: 'email', message: 'must be a well-formed email address' }] }),
    })

    const error = await apiFetch('/auth/signup', { method: 'POST', auth: false, body: '{}' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).fieldErrors()).toEqual({ email: 'must be a well-formed email address' })
  })

  it('returns undefined for 204', async () => {
    mockFetch({ 'POST /api/auth/logout': () => new Response(null, { status: 204 }) })

    await expect(apiFetch('/auth/logout', { method: 'POST', auth: false })).resolves.toBeUndefined()
  })
})

describe('refreshAccessToken', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('returns null and clears the token when there is no session', async () => {
    setAccessToken('old')
    mockFetch({ 'POST /api/auth/refresh': () => problem(401) })

    await expect(refreshAccessToken()).resolves.toBeNull()
    expect(getAccessToken()).toBeNull()
  })

  it('runs inside a Web Lock so tabs take turns', async () => {
    const request = vi.fn((_name: string, task: () => Promise<unknown>) => task())
    vi.stubGlobal('navigator', { ...navigator, locks: { request } })
    mockFetch({ 'POST /api/auth/refresh': () => json(tokens('fresh')) })

    await expect(refreshAccessToken()).resolves.toBe('fresh')
    expect(request).toHaveBeenCalledWith('bankdash-token-refresh', expect.any(Function))
  })
})
