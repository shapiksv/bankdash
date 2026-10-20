import { createContext } from 'react'
import type { CurrentUser, SignupInput } from '../api/auth'

export type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; user: CurrentUser }

export interface AuthContextValue {
  state: AuthState
  login: (email: string, password: string) => Promise<void>
  signup: (input: SignupInput) => Promise<void>
  logout: () => Promise<void>
  /** Reloads the current user, e.g. after a change to the two-factor flag. */
  reloadUser: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
