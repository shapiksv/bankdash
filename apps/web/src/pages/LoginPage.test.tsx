import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { safeRedirect } from '../auth/redirect'
import { jane, renderWithAuth } from '../test/renderWithAuth'

describe('LoginPage', () => {
  it('validates before calling the server', async () => {
    const { auth } = renderWithAuth('/login')

    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription('Enter your email')
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription('Enter your password')
    expect(screen.getByLabelText('Email')).toBeInvalid()
    expect(auth.login).not.toHaveBeenCalled()
  })

  it('logs in and returns to the page that required it', async () => {
    const { auth, router } = renderWithAuth('/login?from=%2Fcredit-cards')

    await userEvent.type(screen.getByLabelText('Email'), ' jane@example.com ')
    await userEvent.type(screen.getByLabelText('Password'), 'secret123')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(auth.login).toHaveBeenCalledWith('jane@example.com', 'secret123')
    expect(router.state.location.pathname).toBe('/credit-cards')
  })

  it('shows one message for wrong credentials', async () => {
    renderWithAuth('/login', { login: vi.fn().mockRejectedValue(new ApiError(401, { status: 401 })) })

    await userEvent.type(screen.getByLabelText('Email'), 'jane@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password')
    expect(screen.getByRole('button', { name: 'Log in' })).toBeEnabled()
  })

  it('shows a generic message for other failures', async () => {
    renderWithAuth('/login', { login: vi.fn().mockRejectedValue(new TypeError('Failed to fetch')) })

    await userEvent.type(screen.getByLabelText('Email'), 'jane@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'secret123')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
  })

  it('sends a logged-in user to the dashboard', () => {
    const { router } = renderWithAuth('/login', { state: { status: 'authenticated', user: jane } })

    expect(router.state.location.pathname).toBe('/')
  })

  it('links to sign up', () => {
    renderWithAuth('/login')

    expect(screen.getByRole('link', { name: 'Sign up' })).toHaveAttribute('href', '/signup')
  })
})

describe('safeRedirect', () => {
  it.each([
    ['/transactions', '/transactions'],
    [null, '/'],
    ['https://evil.example', '/'],
    ['//evil.example', '/'],
  ])('%s → %s', (from, expected) => {
    expect(safeRedirect(from)).toBe(expected)
  })
})
