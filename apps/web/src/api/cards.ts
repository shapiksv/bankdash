import { apiFetch } from './client'

export const ACCOUNT_TYPES = ['CHECKING', 'SAVINGS'] as const
export type AccountType = (typeof ACCOUNT_TYPES)[number]

/** EXPIRED is derived by account-service from the expiry; BLOCKED is final. */
export type CardStatus = 'INACTIVE' | 'ACTIVE' | 'BLOCKED' | 'EXPIRED'

export interface Card {
  id: string
  accountId: string
  type: 'DEBIT'
  /** Always masked by the backend, e.g. "**** **** **** 5678"; the full number never reaches the browser. */
  maskedNumber: string
  holderName: string
  /** MM/YY */
  expiry: string
  status: CardStatus
}

export interface NewCard {
  accountType: AccountType
  pin: string
}

/** @param limit at most this many cards, oldest first; all of them without it */
export async function fetchCards(limit?: number): Promise<Card[]> {
  const query = limit === undefined ? '' : `?limit=${limit}`
  return (await apiFetch<{ cards: Card[] }>(`/cards${query}`)).cards
}

export function addCard(card: NewCard): Promise<Card> {
  return apiFetch<Card>('/cards', { method: 'POST', body: JSON.stringify(card) })
}

/** Sets the first PIN (which activates the card) or changes it; a weak PIN is a 422 with the broken rule. */
export function setCardPin(cardId: string, pin: string): Promise<void> {
  return apiFetch<void>(`/cards/${encodeURIComponent(cardId)}/pin`, { method: 'PUT', body: JSON.stringify({ pin }) })
}

/** Final: there is no unblocking. */
export function blockCard(cardId: string): Promise<void> {
  return apiFetch<void>(`/cards/${encodeURIComponent(cardId)}/block`, { method: 'POST' })
}
