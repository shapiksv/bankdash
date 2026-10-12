import {
  ArrowLeftRight,
  ChartColumn,
  CreditCard,
  HandCoins,
  House,
  Lightbulb,
  Settings,
  User,
  Wrench,
  type LucideIcon,
} from 'lucide-react'

export interface NavItem {
  label: string
  /** Page title shown in the header. */
  title: string
  path: string
  icon: LucideIcon
  /** Shown in the sidebar but not navigable (no page in the MVP). */
  disabled?: boolean
}

/** Sidebar items, in the order of the mockup. */
export const navItems: NavItem[] = [
  { label: 'Dashboard', title: 'Overview', path: '/', icon: House },
  { label: 'Transactions', title: 'Transactions', path: '/transactions', icon: ArrowLeftRight },
  { label: 'Accounts', title: 'Accounts', path: '/accounts', icon: User },
  { label: 'Investments', title: 'Investments', path: '/investments', icon: ChartColumn },
  { label: 'Credit Cards', title: 'Credit Cards', path: '/credit-cards', icon: CreditCard },
  { label: 'Loans', title: 'Loans', path: '/loans', icon: HandCoins },
  { label: 'Services', title: 'Services', path: '/services', icon: Wrench },
  { label: 'My Privileges', title: 'My Privileges', path: '/privileges', icon: Lightbulb, disabled: true },
  { label: 'Setting', title: 'Setting', path: '/settings', icon: Settings },
]
