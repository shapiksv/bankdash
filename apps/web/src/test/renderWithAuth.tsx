import { render } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { vi } from 'vitest'
import type { AuthContextValue, AuthState } from '../auth/AuthContext'
import { routes } from '../routes'
import { FakeAuth } from './FakeAuth'

export { jane } from './users'

interface FakeAuthOptions {
  state?: AuthState
  /** Replace to simulate server failures; by default every call succeeds. */
  login?: AuthContextValue['login']
  signup?: AuthContextValue['signup']
}

/**
 * Renders the real routes with an in-memory auth context: login and signup succeed (unless overridden) and
 * switch to authenticated, logout switches to anonymous. The returned functions are spies.
 */
export function renderWithAuth(path: string, options: FakeAuthOptions = {}) {
  const auth = {
    login: vi.fn(options.login ?? (async () => {})),
    signup: vi.fn(options.signup ?? (async () => {})),
    logout: vi.fn(async () => {}),
  }
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(
    <FakeAuth initial={options.state ?? { status: 'anonymous' }} spies={auth}>
      <RouterProvider router={router} />
    </FakeAuth>,
  )
  return { router, auth }
}
