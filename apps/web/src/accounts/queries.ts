import { useQuery } from '@tanstack/react-query'
import { fetchAccountSummary, type AccountSummary } from '../api/accounts'
import { useUserId } from '../auth/useUserId'

export type SummaryView =
  | { kind: 'loading' }
  | { kind: 'pending' }
  | { kind: 'error' }
  | { kind: 'ready'; summary: Extract<AccountSummary, { status: 'READY' }> }

/**
 * The Accounts page's balances as one of four states. A new user's accounts are opened asynchronously after
 * signup, so PENDING is a normal state, not an error.
 */
export function useAccountSummary(): { view: SummaryView; retry: () => void } {
  const userId = useUserId()
  const query = useQuery({
    queryKey: ['accounts', 'summary', userId],
    queryFn: fetchAccountSummary,
    enabled: !!userId,
  })

  let view: SummaryView = { kind: 'loading' }
  if (query.isError) view = { kind: 'error' }
  else if (query.data?.status === 'READY') view = { kind: 'ready', summary: query.data }
  else if (query.data?.status === 'PENDING') view = { kind: 'pending' }

  return { view, retry: () => void query.refetch() }
}
