import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import * as authApi from '../api/auth'
import { onSessionExpired, refreshAccessToken } from '../api/client'
import { AuthContext, type AuthContextValue, type AuthState } from './AuthContext'

/**
 * Holds who is logged in. On start it tries to restore the session from the refresh cookie, since the access
 * token lives in memory and is gone after a reload.
 *
 * {@link onSignedOut} runs whenever the state becomes anonymous (logout or an expired session), e.g. to drop
 * cached data of the previous user.
 */
export function AuthProvider({ children, onSignedOut }: { children: ReactNode; onSignedOut?: () => void }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' })

  useEffect(() => {
    if (state.status === 'anonymous') onSignedOut?.()
  }, [state.status, onSignedOut])

  const loadUser = useCallback(async () => {
    const user = await authApi.fetchCurrentUser()
    setState({ status: 'authenticated', user })
  }, [])

  useEffect(() => {
    let cancelled = false
    onSessionExpired(() => setState({ status: 'anonymous' }))
    refreshAccessToken()
      .then((token) => (token ? authApi.fetchCurrentUser() : null))
      .then((user) => {
        if (!cancelled) setState(user ? { status: 'authenticated', user } : { status: 'anonymous' })
      })
      .catch(() => {
        if (!cancelled) setState({ status: 'anonymous' })
      })
    return () => {
      cancelled = true
      onSessionExpired(null)
    }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      state,
      login: async (email, password) => {
        await authApi.login(email, password)
        await loadUser()
      },
      signup: async (input) => {
        await authApi.signup(input)
        await loadUser()
      },
      logout: async () => {
        try {
          await authApi.logout()
        } finally {
          setState({ status: 'anonymous' })
        }
      },
      reloadUser: loadUser,
    }),
    [state, loadUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
