import { useState, type ChangeEvent, type FormEvent } from 'react'
import * as authApi from '../../api/auth'
import { ApiError } from '../../api/client'
import { useAuth } from '../../auth/useAuth'
import { validateNewPassword, withoutEmpty } from '../../auth/validation'
import { FormAlert, FormNotice, PrimaryButton, TextField, Toggle } from '../../components/form'

/**
 * Two-factor flag and password change, saved together. Changing the password logs out every other device;
 * this tab gets a new session from the response.
 */
export function SecurityTab() {
  const { state, reloadUser } = useAuth()
  const savedTwoFactor = state.status === 'authenticated' && state.user.twoFactorEnabled
  const [twoFactor, setTwoFactor] = useState(savedTwoFactor)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const [saving, setSaving] = useState(false)

  function edit(field: string, setValue: (value: string) => void) {
    return (event: ChangeEvent<HTMLInputElement>) => {
      setValue(event.target.value)
      setErrors(({ [field]: _cleared, ...rest }) => rest)
      setNotice(undefined)
    }
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const changesPassword = currentPassword !== '' || newPassword !== ''
    const found = changesPassword
      ? withoutEmpty({
          currentPassword: currentPassword ? undefined : 'Enter your current password',
          newPassword: validateNewPassword(newPassword),
        })
      : {}
    setErrors(found)
    setFormError(undefined)
    setNotice(undefined)
    if (Object.keys(found).length > 0) return
    if (!changesPassword && twoFactor === savedTwoFactor) {
      setNotice('Nothing to save')
      return
    }

    setSaving(true)
    try {
      if (twoFactor !== savedTwoFactor) {
        await authApi.setTwoFactor(twoFactor)
        await reloadUser()
      }
      if (changesPassword) {
        await authApi.changePassword(currentPassword, newPassword)
        setCurrentPassword('')
        setNewPassword('')
      }
      setNotice(changesPassword ? 'Password changed. Other devices have been logged out.' : 'Security settings saved')
    } catch (error) {
      if (error instanceof ApiError && error.problem.errors?.length) {
        setErrors(error.fieldErrors())
      } else {
        setFormError('Your security settings could not be saved. Please try again.')
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <form noValidate onSubmit={onSubmit} aria-label="Security" className="flex flex-col gap-6">
      {formError && <FormAlert>{formError}</FormAlert>}
      {notice && <FormNotice>{notice}</FormNotice>}
      <fieldset className="flex flex-col gap-4">
        <legend className="mb-4 text-lg font-medium text-title">Two-factor Authentication</legend>
        <Toggle
          label="Enable or disable two factor authentication"
          checked={twoFactor}
          onChange={(checked) => {
            setTwoFactor(checked)
            setNotice(undefined)
          }}
        />
      </fieldset>
      <fieldset className="flex flex-col gap-5 md:max-w-md">
        <legend className="mb-4 text-lg font-medium text-title">Change Password</legend>
        <TextField
          label="Current Password"
          type="password"
          autoComplete="current-password"
          value={currentPassword}
          onChange={edit('currentPassword', setCurrentPassword)}
          error={errors.currentPassword}
        />
        <TextField
          label="New Password"
          type="password"
          autoComplete="new-password"
          value={newPassword}
          onChange={edit('newPassword', setNewPassword)}
          error={errors.newPassword}
          hint="At least 8 characters"
        />
      </fieldset>
      <div className="flex justify-end">
        <PrimaryButton type="submit" disabled={saving} className="w-full md:w-48">
          {saving ? 'Saving…' : 'Save'}
        </PrimaryButton>
      </div>
    </form>
  )
}
