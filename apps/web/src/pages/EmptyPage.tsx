interface EmptyPageProps {
  name: string
}

/** Placeholder content until a page gets its widgets. */
export function EmptyPage({ name }: EmptyPageProps) {
  return (
    <section
      aria-label={name}
      className="grid min-h-80 place-items-center rounded-3xl border-2 border-dashed border-border bg-surface text-muted"
    >
      <p>{name} — coming soon</p>
    </section>
  )
}
