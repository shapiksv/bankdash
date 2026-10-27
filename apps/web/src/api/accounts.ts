import { apiFetch } from './client'

/**
 * Amounts are decimal strings with 2 decimals (e.g. "1250.50"), never numbers: they are shown as they are and
 * never converted to a JavaScript number.
 */
export type AccountSummary =
  | { status: 'READY'; currency: 'USD'; myBalance: string; totalSaving: string }
  /** The accounts are opened asynchronously after signup, so a new user is PENDING for a few seconds. */
  | { status: 'PENDING'; currency: 'USD'; myBalance: null; totalSaving: null }

export function fetchAccountSummary(): Promise<AccountSummary> {
  return apiFetch<AccountSummary>('/accounts/summary')
}
