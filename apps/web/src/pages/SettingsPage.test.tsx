import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { json, problem } from '../test/fetchMock'
import { jane, janePreferences, janeProfile, renderWithAuth } from '../test/renderWithAuth'

const authenticated = { status: 'authenticated', user: jane } as const

function bodyOf(calls: Request[], method: string, path: string) {
  const call = calls.find((c) => c.method === method && new URL(c.url).pathname === path)
  if (!call) throw new Error(`No ${method} ${path}`)
  return call
}

const echo = async (request: Request) => json({ ...janeProfile, ...(await request.clone().json()) })

describe('Settings', () => {
  it('opens Edit Profile first and switches tabs through the URL', async () => {
    const { router } = renderWithAuth('/settings', { state: authenticated })

    expect(screen.getByRole('tab', { name: 'Edit Profile' })).toHaveAttribute('aria-selected', 'true')
    await userEvent.click(screen.getByRole('tab', { name: 'Security' }))

    expect(router.state.location.search).toBe('?tab=security')
    expect(screen.getByRole('tab', { name: 'Security' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('form', { name: 'Security' })).toBeInTheDocument()
  })

  describe('Edit Profile', () => {
    it('shows the profile and the login email read-only', async () => {
      renderWithAuth('/settings', { state: authenticated })

      expect(await screen.findByLabelText('Your Name')).toHaveValue('Jane Doe')
      expect(screen.getByLabelText('User Name')).toHaveValue('jane')
      expect(screen.getByLabelText('Email')).toHaveValue('jane@example.com')
      expect(screen.getByLabelText('Email')).toHaveAttribute('readonly')
    })

    it('saves the edited fields and sends empty ones as null', async () => {
      const { api } = renderWithAuth('/settings', { state: authenticated, api: { 'PUT /api/profile/me': echo } })
      await userEvent.clear(await screen.findByLabelText('Your Name'))
      await userEvent.type(screen.getByLabelText('Your Name'), 'Charlene Reed')
      await userEvent.type(screen.getByLabelText('City'), '  San Jose ')
      await userEvent.type(screen.getByLabelText('Date of Birth'), '1990-01-25')

      await userEvent.click(screen.getByRole('button', { name: 'Save' }))

      expect(await screen.findByRole('status')).toHaveTextContent('Profile saved')
      expect(await bodyOf(api.calls, 'PUT', '/api/profile/me').json()).toEqual({
        fullName: 'Charlene Reed',
        username: 'jane',
        dateOfBirth: '1990-01-25',
        presentAddress: null,
        permanentAddress: null,
        city: 'San Jose',
        postalCode: null,
        country: null,
      })
    })

    it('validates before sending', async () => {
      const { api } = renderWithAuth('/settings', { state: authenticated })
      await userEvent.clear(await screen.findByLabelText('Your Name'))
      await userEvent.clear(screen.getByLabelText('User Name'))
      await userEvent.type(screen.getByLabelText('User Name'), 'no spaces')

      await userEvent.click(screen.getByRole('button', { name: 'Save' }))

      expect(screen.getByLabelText('Your Name')).toHaveAccessibleDescription('Enter your name')
      expect(screen.getByLabelText('User Name')).toHaveAccessibleDescription(
        'Use 3 to 30 letters, digits, dots, dashes or underscores',
      )
      expect(api.calls.some((c) => c.method === 'PUT')).toBe(false)
    })

    it('shows a taken username next to the field', async () => {
      renderWithAuth('/settings', {
        state: authenticated,
        api: {
          'PUT /api/profile/me': () =>
            problem(409, { errors: [{ field: 'username', message: 'This username is already taken' }] }),
        },
      })
      await screen.findByLabelText('User Name')

      await userEvent.click(screen.getByRole('button', { name: 'Save' }))

      expect(await screen.findByText('This username is already taken')).toBeInTheDocument()
    })

    it('uploads a chosen avatar and shows it', async () => {
      let uploaded = false
      const { api } = renderWithAuth('/settings', {
        state: authenticated,
        api: {
          'GET /api/profile/me': () =>
            json({ ...janeProfile, avatarUpdatedAt: uploaded ? '2026-10-13T10:00:00Z' : null }),
          'PUT /api/profile/me/avatar': () => {
            uploaded = true
            return new Response(null, { status: 204 })
          },
          'GET /api/profile/me/avatar': () =>
            new Response(new Uint8Array([0x89, 0x50, 0x4e, 0x47]), { headers: { 'Content-Type': 'image/png' } }),
        },
      })
      await screen.findByLabelText('Your Name')
      const file = new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], 'me.png', { type: 'image/png' })

      await userEvent.upload(screen.getByTestId('avatar-input'), file)

      expect(await screen.findByRole('img', { name: 'Your avatar' })).toHaveAttribute('src', 'data:image/png;base64,iVBORw==')
      const upload = bodyOf(api.calls, 'PUT', '/api/profile/me/avatar')
      expect(upload.headers.get('Content-Type')).toBe('image/png')
      expect(new Uint8Array(await upload.arrayBuffer())).toEqual(new Uint8Array([0x89, 0x50, 0x4e, 0x47]))
    })

    it('refuses an avatar over 1 MB without uploading it', async () => {
      const { api } = renderWithAuth('/settings', { state: authenticated })
      await screen.findByLabelText('Your Name')
      const big = new File([new Uint8Array(1024 * 1024 + 1)], 'big.png', { type: 'image/png' })

      await userEvent.upload(screen.getByTestId('avatar-input'), big)

      expect(screen.getByRole('alert')).toHaveTextContent('The image must be at most 1 MB')
      expect(api.calls.some((c) => c.method === 'PUT')).toBe(false)
    })

    it('says so while the profile is still being created', async () => {
      renderWithAuth('/settings', { state: authenticated, api: { 'GET /api/profile/me': () => problem(404) } })

      expect(await screen.findByRole('alert')).toHaveTextContent('still setting up your profile')
    })
  })

  describe('Preferences', () => {
    it('shows and saves the preferences', async () => {
      const { api } = renderWithAuth('/settings?tab=preferences', {
        state: authenticated,
        api: { 'PUT /api/preferences': async (request) => json(await request.clone().json()) },
      })
      const form = await screen.findByRole('form', { name: 'Preferences' })
      expect(within(form).getByLabelText('Currency')).toHaveValue('USD')
      expect(within(form).getByLabelText('Time Zone')).toHaveValue('UTC')
      expect(within(form).getByRole('switch', { name: 'I receive merchant order' })).toHaveAttribute('aria-checked', 'false')

      await userEvent.selectOptions(within(form).getByLabelText('Currency'), 'EUR')
      await userEvent.selectOptions(within(form).getByLabelText('Time Zone'), 'Europe/Berlin')
      await userEvent.click(within(form).getByRole('switch', { name: 'I receive merchant order' }))
      await userEvent.click(within(form).getByRole('button', { name: 'Save' }))

      expect(await screen.findByRole('status')).toHaveTextContent('Preferences saved')
      expect(await bodyOf(api.calls, 'PUT', '/api/preferences').json()).toEqual({
        ...janePreferences,
        currency: 'EUR',
        timeZone: 'Europe/Berlin',
        notifyOnMerchantOrders: true,
      })
    })

    it('shows field errors from the server', async () => {
      renderWithAuth('/settings?tab=preferences', {
        state: authenticated,
        api: { 'PUT /api/preferences': () => problem(400, { errors: [{ field: 'timeZone', message: 'Unknown time zone' }] }) },
      })
      await screen.findByRole('form', { name: 'Preferences' })

      await userEvent.click(screen.getByRole('button', { name: 'Save' }))

      expect(await screen.findByText('Unknown time zone')).toBeInTheDocument()
    })
  })

  describe('Security', () => {
    it('changes the password and clears the fields', async () => {
      const { api } = renderWithAuth('/settings?tab=security', {
        state: authenticated,
        api: { 'PUT /api/auth/password': () => json({ accessToken: 't2', tokenType: 'Bearer', expiresIn: 900 }) },
      })
      await userEvent.type(screen.getByLabelText('Current Password'), 'secret123')
      await userEvent.type(screen.getByLabelText('New Password'), 'new-secret-456')

      await userEvent.click(screen.getByRole('button', { name: 'Save' }))

      expect(await screen.findByRole('status')).toHaveTextContent('Other devices have been logged out')
      expect(await bodyOf(api.calls, 'PUT', '/api/auth/password').json()).toEqual({
        currentPassword: 'secret123',
        newPassword: 'new-secret-456',
      })
      expect(screen.getByLabelText('Current Password')).toHaveValue('')
      expect(screen.getByLabelText('New Password')).toHaveValue('')
    })

    it('validates the password fields before sending', async () => {
      const { api } = renderWithAuth('/settings?tab=security', { state: authenticated })
      await userEvent.type(screen.getByLabelText('New Password'), 'short')

      await userEvent.click(screen.getByRole('button', { name: 'Save' }))

      expect(screen.getByLabelText('Current Password')).toHaveAccessibleDescription('Enter your current password')
      expect(screen.getByLabelText('New Password')).toHaveAccessibleDescription('Use at least 8 characters')
      expect(api.calls.some((c) => c.method === 'PUT')).toBe(false)
    })

    it('shows a wrong current password next to the field', async () => {
      renderWithAuth('/settings?tab=security', {
        state: authenticated,
        api: {
          'PUT /api/auth/password': () =>
            problem(400, { errors: [{ field: 'currentPassword', message: 'Current password is incorrect' }] }),
        },
      })
      await userEvent.type(screen.getByLabelText('Current Password'), 'wrong-one')
      await userEvent.type(screen.getByLabelText('New Password'), 'new-secret-456')

      await userEvent.click(screen.getByRole('button', { name: 'Save' }))

      expect(await screen.findByText('Current password is incorrect')).toBeInTheDocument()
    })

    it('saves the two-factor flag and reloads the user', async () => {
      const { api, auth } = renderWithAuth('/settings?tab=security', {
        state: authenticated,
        api: { 'PUT /api/auth/two-factor': () => json({ twoFactorEnabled: true }) },
      })
      const toggle = screen.getByRole('switch', { name: 'Enable or disable two factor authentication' })
      expect(toggle).toHaveAttribute('aria-checked', 'false')

      await userEvent.click(toggle)
      await userEvent.click(screen.getByRole('button', { name: 'Save' }))

      expect(await screen.findByRole('status')).toHaveTextContent('Security settings saved')
      expect(await bodyOf(api.calls, 'PUT', '/api/auth/two-factor').json()).toEqual({ enabled: true })
      expect(auth.reloadUser).toHaveBeenCalledOnce()
      expect(api.calls.some((c) => new URL(c.url).pathname === '/api/auth/password')).toBe(false)
    })
  })
})
