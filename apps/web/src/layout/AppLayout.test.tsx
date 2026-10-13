import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { jane, renderWithAuth } from '../test/renderWithAuth'

function renderAt(path: string) {
  return renderWithAuth(path, { state: { status: 'authenticated', user: jane } }).router
}

describe('AppLayout', () => {
  it('renders every sidebar item from the mockup', () => {
    renderAt('/')

    const nav = screen.getByRole('navigation', { name: 'Main' })
    const labels = within(nav)
      .getAllByRole('listitem')
      .map((item) => item.textContent)

    expect(labels).toEqual([
      'Dashboard',
      'Transactions',
      'Accounts',
      'Investments',
      'Credit Cards',
      'Loans',
      'Services',
      'My Privileges',
      'Setting',
    ])
  })

  it('marks the current page as active and shows its title', () => {
    renderAt('/')

    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Overview')
  })

  it('navigates when a sidebar item is clicked', async () => {
    const router = renderAt('/')

    await userEvent.click(screen.getByRole('link', { name: 'Credit Cards' }))

    expect(router.state.location.pathname).toBe('/credit-cards')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Credit Cards')
    expect(screen.getByRole('link', { name: 'Credit Cards' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Dashboard' })).not.toHaveAttribute('aria-current')
  })

  it('shows My Privileges as disabled, not as a link', () => {
    renderAt('/')

    expect(screen.queryByRole('link', { name: 'My Privileges' })).not.toBeInTheDocument()
    expect(screen.getByText('My Privileges').closest('[aria-disabled]')).toHaveAttribute('aria-disabled', 'true')
  })

  it('renders the header controls', () => {
    renderAt('/transactions')

    expect(screen.getByPlaceholderText('Search for something')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Notifications' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Settings' })).toHaveAttribute('href', '/settings')
  })

  it('shows a not-found page for unknown routes', () => {
    renderAt('/does-not-exist')

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Not found')
  })

  it('shows the user and logs out from the account menu', async () => {
    const { auth, router } = renderWithAuth('/settings', { state: { status: 'authenticated', user: jane } })

    await userEvent.click(screen.getByRole('button', { name: 'Account menu' }))
    const menu = screen.getByRole('menu', { name: 'Account' })
    expect(menu).toHaveTextContent('jane@example.com')

    await userEvent.click(within(menu).getByRole('menuitem', { name: 'Log out' }))

    expect(auth.logout).toHaveBeenCalledOnce()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('closes the account menu with Escape', async () => {
    renderAt('/')

    await userEvent.click(screen.getByRole('button', { name: 'Account menu' }))
    await userEvent.keyboard('{Escape}')

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
})
