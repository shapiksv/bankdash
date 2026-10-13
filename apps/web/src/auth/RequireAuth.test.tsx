import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { jane, renderWithAuth } from '../test/renderWithAuth'

describe('RequireAuth', () => {
  it('sends anonymous users to login and remembers where they were going', () => {
    const { router } = renderWithAuth('/transactions?page=2')

    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.search).toBe('?from=%2Ftransactions%3Fpage%3D2')
    expect(screen.getByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
  })

  it('does not add ?from for the dashboard', () => {
    const { router } = renderWithAuth('/')

    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.search).toBe('')
  })

  it('waits while the session is being restored', () => {
    const { router } = renderWithAuth('/accounts', { state: { status: 'loading' } })

    expect(screen.getByRole('status')).toHaveTextContent('Loading')
    expect(router.state.location.pathname).toBe('/accounts')
  })

  it('renders the app for authenticated users', async () => {
    renderWithAuth('/accounts', { state: { status: 'authenticated', user: jane } })

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Accounts')
    expect(await within(screen.getByRole('button', { name: 'Account menu' })).findByText('JD')).toBeInTheDocument()
  })

  it('keeps login and signup public', () => {
    renderWithAuth('/signup')

    expect(screen.getByRole('heading', { name: 'Create your account' })).toBeInTheDocument()
  })
})
