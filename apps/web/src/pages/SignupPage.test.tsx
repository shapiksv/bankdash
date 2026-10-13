import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { renderWithAuth } from '../test/renderWithAuth'

async function fillIn(fullName: string, email: string, password: string) {
  if (fullName) await userEvent.type(screen.getByLabelText('Full name'), fullName)
  if (email) await userEvent.type(screen.getByLabelText('Email'), email)
  if (password) await userEvent.type(screen.getByLabelText('Password'), password)
  await userEvent.click(screen.getByRole('button', { name: 'Sign up' }))
}

describe('SignupPage', () => {
  it('validates every field before calling the server', async () => {
    const { auth } = renderWithAuth('/signup')

    await fillIn('', 'not-an-email', 'short')

    expect(screen.getByLabelText('Full name')).toHaveAccessibleDescription('Enter your full name')
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription('Enter a valid email address')
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription('Use at least 8 characters')
    expect(auth.signup).not.toHaveBeenCalled()
  })

  it('clears a field error as soon as the field is edited', async () => {
    renderWithAuth('/signup')
    await userEvent.click(screen.getByRole('button', { name: 'Sign up' }))

    await userEvent.type(screen.getByLabelText('Full name'), 'J')

    expect(screen.getByLabelText('Full name')).toBeValid()
    expect(screen.queryByText('Enter your full name')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription('Enter your email')
  })

  it('shows the password rule as a hint', () => {
    renderWithAuth('/signup')

    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription('At least 8 characters')
  })

  it('signs up and goes to the dashboard', async () => {
    const { auth, router } = renderWithAuth('/signup')

    await fillIn(' Jane Doe ', 'jane@example.com', 'secret123')

    expect(auth.signup).toHaveBeenCalledWith({ fullName: 'Jane Doe', email: 'jane@example.com', password: 'secret123' })
    expect(router.state.location.pathname).toBe('/')
  })

  it('puts a duplicate email next to the email field', async () => {
    renderWithAuth('/signup', { signup: vi.fn().mockRejectedValue(new ApiError(409, { status: 409 })) })

    await fillIn('Jane Doe', 'jane@example.com', 'secret123')

    expect(await screen.findByText('An account with this email already exists')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toBeInvalid()
  })

  it('shows server validation errors on their fields', async () => {
    const error = new ApiError(400, { status: 400, errors: [{ field: 'password', message: 'Password must be at most 72 bytes long' }] })
    renderWithAuth('/signup', { signup: vi.fn().mockRejectedValue(error) })

    await fillIn('Jane Doe', 'jane@example.com', 'secret123')

    expect(await screen.findByText('Password must be at most 72 bytes long')).toBeInTheDocument()
  })

  it('links to log in', () => {
    renderWithAuth('/signup')

    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login')
  })
})
