import { NavLink } from 'react-router'
import { navItems } from '../navigation'
import { Logo } from './Logo'

interface SidebarProps {
  open: boolean
  onNavigate: () => void
}

export function Sidebar({ open, onNavigate }: SidebarProps) {
  return (
    <aside
      className={`fixed inset-y-0 left-0 z-30 w-64 border-r border-border bg-surface transition-transform lg:translate-x-0 ${
        open ? 'translate-x-0' : '-translate-x-full'
      }`}
    >
      <div className="flex h-24 items-center px-9">
        <Logo />
      </div>
      <nav aria-label="Main">
        <ul className="space-y-1">
          {navItems.map(({ label, path, icon: Icon, disabled }) => (
            <li key={path}>
              {disabled ? (
                <span
                  aria-disabled="true"
                  title="Coming soon"
                  className="flex cursor-not-allowed items-center gap-6 px-9 py-4 text-lg font-medium text-nav-inactive opacity-60"
                >
                  <Icon className="size-6" aria-hidden />
                  {label}
                </span>
              ) : (
                <NavLink
                  to={path}
                  end={path === '/'}
                  onClick={onNavigate}
                  className={({ isActive }) =>
                    `relative flex items-center gap-6 px-9 py-4 text-lg font-medium transition-colors ${
                      isActive
                        ? 'text-primary before:absolute before:inset-y-0 before:left-0 before:w-1.5 before:rounded-r-lg before:bg-primary'
                        : 'text-nav-inactive hover:text-title'
                    }`
                  }
                >
                  <Icon className="size-6" aria-hidden />
                  {label}
                </NavLink>
              )}
            </li>
          ))}
        </ul>
      </nav>
    </aside>
  )
}
