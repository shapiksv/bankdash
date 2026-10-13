interface LogoProps {
  /** "light" for use on the blue card gradient. */
  tone?: 'dark' | 'light'
}

export function Logo({ tone = 'dark' }: LogoProps) {
  const light = tone === 'light'
  return (
    <span className={`flex items-center gap-2 text-2xl font-extrabold ${light ? 'text-white' : 'text-title'}`}>
      <span
        aria-hidden
        className={`grid size-8 place-items-center rounded-lg text-sm ${light ? 'bg-white text-card-to' : 'bg-title text-white'}`}
      >
        B
      </span>
      BankDash.
    </span>
  )
}
