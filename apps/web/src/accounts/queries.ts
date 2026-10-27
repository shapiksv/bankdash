import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { fetchAccountSummary, type AccountSummary } from '../api/accounts'
import { useUserId } from '../auth/useUserId'

/** While PENDING the summary is requested again this often... */
export const POLL_INTERVAL_MS = 2000
/** ...for at most this long, counted from the first PENDING response (e.g. the event went to the DLT). */
export const POLL_LIMIT_MS = 30_000

export type SummaryView =
  | { kind: 'loading' }
  | { kind: 'pending' }
  | { kind: 'error' }
  | { kind: 'ready'; summary: Extract<AccountSummary, { status: 'READY' }> }

/**
 * The Accounts page's balances as one of four states. A new user's accounts are opened asynchronously after
 * signup, so PENDING is a normal state, not an error: it is polled, and only a PENDING that outlasts
 * {@link POLL_LIMIT_MS} becomes an error. This polling is separate from the 404 retry of queryClient.ts.
 */
export function useAccountSummary(): { view: SummaryView; retry: () => void } {
  const userId = useUserId()
  const pendingSince = useRef<number | null>(null)
  const [timedOut, setTimedOut] = useState(false)
  const query = useQuery({
    queryKey: ['accounts', 'summary', userId],
    queryFn: fetchAccountSummary,
    enabled: !!userId,
    refetchInterval: (q) =>
      q.state.status !== 'error' && q.state.data?.status === 'PENDING' && !timedOut ? POLL_INTERVAL_MS : false,
  })

  // Measured when a response arrives, so no second timer has to stay in sync with the query.
  const { data, dataUpdatedAt } = query
  useEffect(() => {
    if (data?.status !== 'PENDING') {
      pendingSince.current = null
      return
    }
    pendingSince.current ??= dataUpdatedAt
    if (dataUpdatedAt - pendingSince.current >= POLL_LIMIT_MS) setTimedOut(true)
  }, [data, dataUpdatedAt])

  let view: SummaryView = { kind: 'loading' }
  if (query.isError || (timedOut && data?.status === 'PENDING')) view = { kind: 'error' }
  else if (query.data?.status === 'READY') view = { kind: 'ready', summary: query.data }
  else if (query.data?.status === 'PENDING') view = { kind: 'pending' }

  const retry = () => {
    pendingSince.current = null
    setTimedOut(false)
    void query.refetch()
  }
  return { view, retry }
}
