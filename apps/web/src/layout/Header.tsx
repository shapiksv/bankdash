import { Bell, Menu, Search, Settings } from 'lucide-react'
import { Link } from 'react-router'
import { UserMenu } from './UserMenu'

interface HeaderProps {
  title: string
  onMenuClick: () => void
}

export function Header({ title, onMenuClick }: HeaderProps) {
  return (
    <header className="sticky top-0 z-10 flex h-24 items-center gap-4 border-b border-border bg-surface px-6 lg:px-10">
      <button
        type="button"
        onClick={onMenuClick}
        aria-label="Open menu"
        className="grid size-10 place-items-center rounded-lg text-title hover:bg-canvas lg:hidden"
      >
        <Menu className="size-6" />
      </button>

      <h1 className="flex-1 text-2xl font-semibold text-title lg:text-3xl">{title}</h1>

      <label className="hidden h-12 w-64 items-center gap-3 rounded-full bg-canvas px-6 text-muted md:flex">
        <Search className="size-5" aria-hidden />
        <input
          type="search"
          placeholder="Search for something"
          className="w-full bg-transparent text-sm text-title placeholder:text-muted focus:outline-none"
        />
      </label>

      <Link
        to="/settings"
        aria-label="Settings"
        className="hidden size-12 place-items-center rounded-full bg-canvas text-muted hover:text-primary sm:grid"
      >
        <Settings className="size-6" />
      </Link>

      <button
        type="button"
        aria-label="Notifications"
        className="hidden size-12 place-items-center rounded-full bg-canvas text-danger sm:grid"
      >
        <Bell className="size-6" />
      </button>

      <UserMenu />
    </header>
  )
}
