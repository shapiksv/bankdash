import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getAccessToken, setAccessToken } from '../api/client'
import { json, mockFetch, problem } from '../test/fetchMock'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './useAuth'

const tokens = (accessToken: string) => ({ accessToken, tokenType: 'Bearer', expiresIn: 900 })
const me = { id: 'u1', email: 'jane@example.com' }

function Probe() {
  const { state, login, logout } = useAuth()
  return (
    <div>
      <p>{state.status === 'authenticated' ? `user:${state.user.email}` : state.status}</p>
      <button onClick={() => login('jane@example.com', 'secret123')}>login</button>
      <button onClick={() => logout()}>logout</button>
    </div>
  )
}

describe('AuthProvider', () => {
  beforeEach(() => setAccessToken(null))
  afterEach(() => vi.unstubAllGlobals())

  it('restores the session from the refresh cookie on start', async () => {
    mockFetch({
      'POST /api/auth/refresh': () => json(tokens('t1')),
      'GET /api/auth/me': () => json(me),
    })

    render(<AuthProvider><Probe /></AuthProvider>)

    expect(screen.getByText('loading')).toBeInTheDocument()
    expect(await screen.findByText('user:jane@example.com')).toBeInTheDocument()
  })

  it('is anonymous when there is no session', async () => {
    mockFetch({ 'POST /api/auth/refresh': () => problem(401) })

    render(<AuthProvider><Probe /></AuthProvider>)

    expect(await screen.findByText('anonymous')).toBeInTheDocument()
  })

  it('logs in and out', async () => {
    const { calls } = mockFetch({
      'POST /api/auth/refresh': () => problem(401),
      'POST /api/auth/login': () => json(tokens('t1')),
      'GET /api/auth/me': () => json(me),
      'POST /api/auth/logout': () => new Response(null, { status: 204 }),
    })
    render(<AuthProvider><Probe /></AuthProvider>)
    await screen.findByText('anonymous')

    await userEvent.click(screen.getByText('login'))
    expect(await screen.findByText('user:jane@example.com')).toBeInTheDocument()
    expect(getAccessToken()).toBe('t1')
    expect(JSON.parse(await calls.find((c) => c.url.endsWith('/login'))!.text())).toEqual({
      email: 'jane@example.com',
      password: 'secret123',
    })

    await userEvent.click(screen.getByText('logout'))
    expect(await screen.findByText('anonymous')).toBeInTheDocument()
    expect(getAccessToken()).toBeNull()
  })

  it('becomes anonymous when a request finds the session expired', async () => {
    let sessionAlive = true
    mockFetch({
      'POST /api/auth/refresh': () => (sessionAlive ? json(tokens('t1')) : problem(401)),
      'GET /api/auth/me': () => (sessionAlive ? json(me) : problem(401)),
    })
    render(<AuthProvider><Probe /></AuthProvider>)
    await screen.findByText('user:jane@example.com')

    sessionAlive = false
    const { fetchCurrentUser } = await import('../api/auth')
    await act(() => fetchCurrentUser().catch(() => undefined))

    expect(screen.getByText('anonymous')).toBeInTheDocument()
  })
})
