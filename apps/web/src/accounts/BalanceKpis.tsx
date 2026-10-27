import { KpiCard } from './KpiCard'
import { formatUsd } from './money'
import { useAccountSummary, type SummaryView } from './queries'

/** My Balance (CHECKING) and Total Saving (SAVINGS). */
export function BalanceKpis() {
  const { view, retry } = useAccountSummary()
  const amount = (pick: (ready: Extract<SummaryView, { kind: 'ready' }>) => string) =>
    view.kind === 'ready' ? formatUsd(pick(view)) : undefined

  return (
    <>
      <KpiCard label="My Balance">
        <KpiBody view={view} amount={amount((v) => v.summary.myBalance)} retry={retry} />
      </KpiCard>
      <KpiCard label="Total Saving">
        <KpiBody view={view} amount={amount((v) => v.summary.totalSaving)} retry={retry} />
      </KpiCard>
    </>
  )
}

interface KpiBodyProps {
  view: SummaryView
  amount: string | undefined
  retry: () => void
}

function KpiBody({ view, amount, retry }: KpiBodyProps) {
  switch (view.kind) {
    case 'loading':
      return (
        <div role="status" aria-busy="true" className="h-8 w-32 animate-pulse rounded-lg bg-field">
          <span className="sr-only">Loading…</span>
        </div>
      )
    case 'pending':
      return <p className="text-muted">Setting up your accounts…</p>
    case 'error':
      return (
        <div className="flex flex-col items-start gap-2">
          <p className="text-danger">Your balance could not be loaded.</p>
          <button type="button" onClick={retry} className="font-semibold text-primary hover:underline">
            Retry
          </button>
        </div>
      )
    case 'ready':
      return <p className="text-2xl font-semibold text-title">{amount}</p>
  }
}
