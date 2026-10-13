import { render } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { vi } from 'vitest'
import { AuthContext, type AuthContextValue, type AuthState } from '../auth/AuthContext'
import { routes } from '../routes'

export const jane = { id: 'u1', email: 'jane@example.com' }

/** Renders the real routes with a fake auth context, so pages can be tested without the network. */
export function renderWithAuth(path: string, overrides: Partial<AuthContextValue> & { state?: AuthState } = {}) {
  const auth: AuthContextValue = {
    state: { status: 'anonymous' },
    login: vi.fn().mockResolvedValue(undefined),
    signup: vi.fn().mockResolvedValue(undefined),
    logout: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  }
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(
    <AuthContext.Provider value={auth}>
      <RouterProvider router={router} />
    </AuthContext.Provider>,
  )
  return { router, auth }
}
