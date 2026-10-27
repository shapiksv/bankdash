import { BalanceKpis } from '../accounts/BalanceKpis'

/** The Accounts screen. So far only the balance KPIs; Income and Expense need transaction history. */
export function AccountsPage() {
  return (
    <div className="grid gap-6 sm:grid-cols-2 xl:grid-cols-4">
      <BalanceKpis />
    </div>
  )
}
