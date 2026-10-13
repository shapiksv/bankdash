import { Outlet } from 'react-router'
import { Logo } from './Logo'

/**
 * Frame for Login and Sign up. The mockup has no auth screens, so this reuses the dashboard's language: the
 * blue "My Cards" gradient as a brand panel, white 25px-radius cards on the canvas background.
 */
export function AuthLayout() {
  return (
    <div className="grid min-h-screen place-items-center bg-canvas p-4 sm:p-6">
      <div className="grid w-full max-w-5xl overflow-hidden rounded-[25px] bg-surface lg:grid-cols-2">
        <aside className="hidden flex-col justify-between gap-12 bg-linear-115 from-card-from to-card-to p-12 text-white lg:flex">
          <Logo tone="light" />
          <div className="space-y-4">
            <h2 className="text-3xl font-semibold leading-tight">All your cards, accounts and transfers in one place.</h2>
            <p className="text-white/70">Track spending, move money and stay on top of your balance.</p>
          </div>
          <DecorativeCard />
        </aside>
        <main className="flex flex-col gap-8 p-6 sm:p-12">
          <div className="lg:hidden">
            <Logo />
          </div>
          <Outlet />
        </main>
      </div>
    </div>
  )
}

function DecorativeCard() {
  return (
    <div aria-hidden className="w-72 rounded-[25px] bg-white/15 p-6 font-semibold backdrop-blur-sm">
      <p className="text-xs font-normal text-white/70">Balance</p>
      <p className="text-xl">$5,756</p>
      <p className="mt-8 text-lg tracking-wider">3778 **** **** 1234</p>
    </div>
  )
}
