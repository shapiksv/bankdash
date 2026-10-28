import pg from 'pg'
import { ACCOUNT_DB } from './env.ts'

/**
 * Direct access to account-service's e2e database, for test data that no API can produce yet (balances, an
 * account that does not exist). Never add test-only endpoints to the services instead.
 */
async function withAccountDb<T>(work: (client: pg.Client) => Promise<T>): Promise<T> {
  const client = new pg.Client(ACCOUNT_DB)
  await client.connect()
  try {
    return await work(client)
  } finally {
    await client.end()
  }
}

/** Amounts as decimal strings, like the API, so nothing passes through a JavaScript number. */
export async function setBalances(userId: string, checking: string, savings: string): Promise<void> {
  await withAccountDb(async (client) => {
    const result = await client.query(
      `UPDATE account SET balance = CASE type WHEN 'CHECKING' THEN $2::numeric ELSE $3::numeric END WHERE user_id = $1`,
      [userId, checking, savings],
    )
    if (result.rowCount !== 2) throw new Error(`Expected 2 accounts for ${userId}, updated ${result.rowCount}`)
  })
}

export interface AccountRow {
  id: string
  user_id: string
  type: string
  currency: string
  balance: string
  created_at: Date
}

/**
 * Deletes the user's SAVINGS account, so the summary is PENDING as if account-service had not opened it yet
 * (BANK-5 AC11). No card is linked to it: the first card belongs to CHECKING. Returns the row for
 * {@link restoreSavings}.
 */
export async function removeSavings(userId: string): Promise<AccountRow> {
  return withAccountDb(async (client) => {
    const result = await client.query<AccountRow>(
      `DELETE FROM account WHERE user_id = $1 AND type = 'SAVINGS' RETURNING id, user_id, type, currency, balance::text, created_at`,
      [userId],
    )
    const [row] = result.rows
    if (!row) throw new Error(`No SAVINGS account for ${userId}`)
    return row
  })
}

/** Puts back a row of {@link removeSavings}: the summary becomes READY again. */
export async function restoreSavings(row: AccountRow): Promise<void> {
  await withAccountDb(async (client) => {
    await client.query(
      `INSERT INTO account (id, user_id, type, currency, balance, created_at) VALUES ($1, $2, $3, $4, $5::numeric, $6)`,
      [row.id, row.user_id, row.type, row.currency, row.balance, row.created_at],
    )
  })
}