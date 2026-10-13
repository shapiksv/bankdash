import type { RouteObject } from 'react-router'
import { AppLayout } from './layout/AppLayout'
import { AuthLayout } from './layout/AuthLayout'
import { AccountsPage } from './pages/AccountsPage'
import { CreditCardsPage } from './pages/CreditCardsPage'
import { DashboardPage } from './pages/DashboardPage'
import { InvestmentsPage } from './pages/InvestmentsPage'
import { LoansPage } from './pages/LoansPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { ServicesPage } from './pages/ServicesPage'
import { SettingsPage } from './pages/SettingsPage'
import { SignupPage } from './pages/SignupPage'
import { TransactionsPage } from './pages/TransactionsPage'

export const routes: RouteObject[] = [
  {
    element: <AuthLayout />,
    children: [
      { path: 'login', element: <LoginPage /> },
      { path: 'signup', element: <SignupPage /> },
    ],
  },
  {
    element: <AppLayout />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'transactions', element: <TransactionsPage /> },
      { path: 'accounts', element: <AccountsPage /> },
      { path: 'investments', element: <InvestmentsPage /> },
      { path: 'credit-cards', element: <CreditCardsPage /> },
      { path: 'loans', element: <LoansPage /> },
      { path: 'services', element: <ServicesPage /> },
      { path: 'settings', element: <SettingsPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
