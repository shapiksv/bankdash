import type { ReactNode } from 'react'

interface KpiCardProps {
  label: string
  children: ReactNode
}

/** One KPI tile of the Accounts page: a label and a value (or its loading, pending or error state). */
export function KpiCard({ label, children }: KpiCardProps) {
  return (
    <section aria-label={label} className="flex min-h-28 flex-col justify-center gap-1 rounded-[25px] bg-surface p-6">
      <h2 className="text-base text-muted">{label}</h2>
      {children}
    </section>
  )
}
