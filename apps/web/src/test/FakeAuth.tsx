import { useMemo, useState, type ReactNode } from 'react'
import { AuthContext, type AuthContextValue, type AuthState } from '../auth/AuthContext'
import { jane } from './users'

/** In-memory auth for tests; see renderWithAuth. */
export function FakeAuth({
  initial,
  spies,
  children,
}: {
  initial: AuthState
  spies: Omit<AuthContextValue, 'state'>
  children: ReactNode
}) {
  const [state, setState] = useState(initial)
  const value = useMemo<AuthContextValue>(
    () => ({
      state,
      login: async (email, password) => {
        await spies.login(email, password)
        setState({ status: 'authenticated', user: { ...jane, email } })
      },
      signup: async (input) => {
        await spies.signup(input)
        setState({ status: 'authenticated', user: { ...jane, email: input.email } })
      },
      logout: async () => {
        await spies.logout()
        setState({ status: 'anonymous' })
      },
    }),
    [state, spies],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
