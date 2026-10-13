import { apiFetch, setAccessToken, type TokenResponse } from './client'

export interface CurrentUser {
  id: string
  email: string
}

export interface SignupInput {
  fullName: string
  email: string
  password: string
}

export async function login(email: string, password: string): Promise<void> {
  const tokens = await apiFetch<TokenResponse>('/auth/login', {
    method: 'POST',
    auth: false,
    body: JSON.stringify({ email, password }),
  })
  setAccessToken(tokens.accessToken)
}

/** Creates the account and logs in: the response carries tokens just like login. */
export async function signup(input: SignupInput): Promise<void> {
  const tokens = await apiFetch<TokenResponse>('/auth/signup', {
    method: 'POST',
    auth: false,
    body: JSON.stringify(input),
  })
  setAccessToken(tokens.accessToken)
}

export async function logout(): Promise<void> {
  try {
    await apiFetch<void>('/auth/logout', { method: 'POST', auth: false })
  } finally {
    setAccessToken(null)
  }
}

export function fetchCurrentUser(): Promise<CurrentUser> {
  return apiFetch<CurrentUser>('/auth/me')
}
