import { randomUUID } from 'node:crypto'
import { expect, type APIRequestContext } from '@playwright/test'

/** A user created for one test; never shared. */
export interface User {
  id: string
  fullName: string
  email: string
  password: string
  /** For calls through {@link Api}; the browser gets its own token from the refresh cookie. */
  accessToken: string
}

export interface Card {
  id: string
  maskedNumber: string
  status: 'INACTIVE' | 'ACTIVE' | 'BLOCKED' | 'EXPIRED'
}

/** Valid PINs under BANK-4's rules (no sequence, repeat, ABAB, year 1950–2029 or keypad pattern). */
export const PINS = ['4826', '7351', '5917', '3846'] as const

export function newUserInput() {
  const suffix = randomUUID()
  return { fullName: `E2E ${suffix.slice(0, 8)}`, email: `e2e+${suffix}@example.com`, password: 'e2e-password-1' }
}

/**
 * Signs up a unique user through the gateway and waits until account-service has opened their accounts and first
 * card (asynchronously, from auth.user-registered.v1). Called with `page.request`, the refresh cookie lands in
 * the page's context, so the next `page.goto` starts logged in, just like a reload after signup.
 */
export async function signUp(request: APIRequestContext): Promise<User> {
  const input = newUserInput()
  const signup = await request.post('/api/auth/signup', { data: input })
  expect(signup.status(), await signup.text()).toBe(201)
  const { accessToken } = (await signup.json()) as { accessToken: string }
  const api = new Api(request, accessToken)
  const { id } = await api.get<{ id: string }>('/api/auth/me')
  await expect
    .poll(async () => (await api.get<{ status: string }>('/api/accounts/summary')).status, {
      message: 'accounts are opened after signup',
      timeout: 20_000,
    })
    .toBe('READY')
  return { id, ...input, accessToken }
}

/** The real API, for setting up state that has an endpoint (cards); see db.ts for state that has none. */
export class Api {
  private readonly request: APIRequestContext
  private readonly token: string

  constructor(request: APIRequestContext, token: string) {
    this.request = request
    this.token = token
  }

  static of(request: APIRequestContext, user: User): Api {
    return new Api(request, user.accessToken)
  }

  async get<T>(path: string): Promise<T> {
    const response = await this.request.get(path, { headers: this.headers() })
    expect(response.status(), `GET ${path}: ${await response.text()}`).toBe(200)
    return (await response.json()) as T
  }

  async cards(): Promise<Card[]> {
    return (await this.get<{ cards: Card[] }>('/api/cards')).cards
  }

  async addCard(accountType: 'CHECKING' | 'SAVINGS', pin: string): Promise<Card> {
    const response = await this.request.post('/api/cards', { headers: this.headers(), data: { accountType, pin } })
    expect(response.status(), `POST /api/cards: ${await response.text()}`).toBe(201)
    return (await response.json()) as Card
  }

  async blockCard(id: string): Promise<void> {
    const response = await this.request.post(`/api/cards/${id}/block`, { headers: this.headers() })
    expect(response.status(), `block ${id}: ${await response.text()}`).toBe(204)
  }

  private headers() {
    return { Authorization: `Bearer ${this.token}` }
  }
}

/** "**** **** **** 5678" → "5678" */
export function last4(card: Card): string {
  return card.maskedNumber.slice(-4)
}