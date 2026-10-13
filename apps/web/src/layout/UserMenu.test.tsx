import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { initials } from '../profile/queries'
import { json, problem } from '../test/fetchMock'
import { jane, janeProfile, renderWithAuth } from '../test/renderWithAuth'

const authenticated = { status: 'authenticated', user: jane } as const

describe('UserMenu', () => {
  it("shows the profile's initials and full name", async () => {
    renderWithAuth('/', { state: authenticated })

    const button = screen.getByRole('button', { name: 'Account menu' })
    expect(await within(button).findByText('JD')).toBeInTheDocument()

    await userEvent.click(button)
    const menu = screen.getByRole('menu', { name: 'Account' })
    expect(menu).toHaveTextContent('Jane Doe')
    expect(menu).toHaveTextContent('jane@example.com')
  })

  it('shows the uploaded avatar', async () => {
    const { api } = renderWithAuth('/', {
      state: authenticated,
      api: {
        'GET /api/profile/me': () => json({ ...janeProfile, avatarUpdatedAt: '2026-10-13T10:00:00Z' }),
        'GET /api/profile/me/avatar': () =>
          new Response(new Uint8Array([0x89, 0x50, 0x4e, 0x47]), { headers: { 'Content-Type': 'image/png' } }),
      },
    })

    const button = screen.getByRole('button', { name: 'Account menu' })
    expect(await within(button).findByRole('presentation')).toHaveAttribute('src', 'data:image/png;base64,iVBORw==')
    expect(api.calls.some((c) => c.url.endsWith('/api/profile/me/avatar'))).toBe(true)
  })

  it('does not fetch an avatar the user does not have', async () => {
    const { api } = renderWithAuth('/', { state: authenticated })

    await within(screen.getByRole('button', { name: 'Account menu' })).findByText('JD')
    expect(api.calls.map((c) => new URL(c.url).pathname)).not.toContain('/api/profile/me/avatar')
  })

  it('uses the email initial while the profile is still being created, then the name', async () => {
    let attempts = 0
    renderWithAuth('/', {
      state: authenticated,
      api: { 'GET /api/profile/me': () => (++attempts < 3 ? problem(404) : json(janeProfile)) },
    })

    const button = screen.getByRole('button', { name: 'Account menu' })
    expect(button).toHaveTextContent('J')
    expect(await within(button).findByText('JD')).toBeInTheDocument()
    expect(attempts).toBe(3)
  })
})

describe('initials', () => {
  it('takes the first and last word of the name', () => {
    expect(initials('charlene van reed', 'x@example.com')).toBe('CR')
    expect(initials('Jane', 'x@example.com')).toBe('J')
  })

  it('falls back to the email', () => {
    expect(initials(undefined, 'jane@example.com')).toBe('J')
    expect(initials('  ', '')).toBe('?')
  })
})
