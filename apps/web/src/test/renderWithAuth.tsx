import { QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { vi } from 'vitest'
import { createQueryClient } from '../api/queryClient'
import type { AuthContextValue, AuthState } from '../auth/AuthContext'
import { routes } from '../routes'
import { FakeAuth } from './FakeAuth'
import { json, mockFetch, type Routes } from './fetchMock'
import { janePreferences, janeProfile } from './users'

export { jane, janePreferences, janeProfile } from './users'

interface FakeAuthOptions {
  state?: AuthState
  /** Replace to simulate server failures; by default every call succeeds. */
  login?: AuthContextValue['login']
  signup?: AuthContextValue['signup']
  /** API responses by "METHOD /api/path", on top of defaults for Jane's profile and preferences. */
  api?: Routes
}

/**
 * Renders the real routes with an in-memory auth context: login and signup succeed (unless overridden) and
 * switch to authenticated, logout switches to anonymous. The returned functions are spies. API calls go to a
 * fetch mock (see {@link mockFetch}); queries retry without delay.
 */
export function renderWithAuth(path: string, options: FakeAuthOptions = {}) {
  const auth = {
    login: vi.fn(options.login ?? (async () => {})),
    signup: vi.fn(options.signup ?? (async () => {})),
    logout: vi.fn(async () => {}),
    reloadUser: vi.fn(async () => {}),
  }
  const api = mockFetch({
    'GET /api/profile/me': () => json(janeProfile),
    'GET /api/preferences': () => json(janePreferences),
    ...options.api,
  })
  const queryClient = createQueryClient({ retryDelay: 0 })
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(
    <QueryClientProvider client={queryClient}>
      <FakeAuth initial={options.state ?? { status: 'anonymous' }} spies={auth}>
        <RouterProvider router={router} />
      </FakeAuth>
    </QueryClientProvider>,
  )
  return { router, auth, api, queryClient }
}
