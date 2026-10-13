import { useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { CURRENCIES, type Currency, type Preferences } from '../../api/profile'
import { FormAlert, FormNotice, PrimaryButton, SelectField, Toggle } from '../../components/form'
import { usePreferences, useUpdatePreferences } from '../../profile/queries'

export function PreferencesTab() {
  const { data, error, isPending } = usePreferences()

  if (isPending) return <p role="status">Loading your preferences…</p>
  if (error) {
    return (
      <FormAlert>
        {error instanceof ApiError && error.status === 404
          ? 'We are still setting up your profile. Please try again in a moment.'
          : 'Your preferences could not be loaded. Please try again.'}
      </FormAlert>
    )
  }
  return <PreferencesForm initial={data} />
}

const CURRENCY_OPTIONS = CURRENCIES.map((code) => ({ value: code, label: code }))

/** Every IANA zone the browser knows, with UTC first and the saved zone kept even if the browser lacks it. */
function timeZoneOptions(saved: string) {
  const zones = Intl.supportedValuesOf('timeZone').filter((zone) => zone !== 'UTC')
  if (saved !== 'UTC' && !zones.includes(saved)) zones.unshift(saved)
  return ['UTC', ...zones].map((zone) => ({ value: zone, label: zone.replaceAll('_', ' ') }))
}

const NOTIFICATIONS: { name: keyof Preferences & `notify${string}`; label: string }[] = [
  { name: 'notifyOnTransfers', label: 'I send or receive digital currency' },
  { name: 'notifyOnMerchantOrders', label: 'I receive merchant order' },
  { name: 'notifyOnRecommendations', label: 'There are recommendation for my account' },
]

function PreferencesForm({ initial }: { initial: Preferences }) {
  const [values, setValues] = useState(initial)
  const [zones] = useState(() => timeZoneOptions(initial.timeZone))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const save = useUpdatePreferences()

  function change<K extends keyof Preferences>(field: K, value: Preferences[K]) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors(({ [field]: _cleared, ...rest }) => rest)
    setNotice(undefined)
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(undefined)
    setNotice(undefined)
    try {
      setValues(await save.mutateAsync(values))
      setNotice('Preferences saved')
    } catch (error) {
      if (error instanceof ApiError && error.problem.errors?.length) {
        setErrors(error.fieldErrors())
      } else {
        setFormError('Your preferences could not be saved. Please try again.')
      }
    }
  }

  return (
    <form noValidate onSubmit={onSubmit} aria-label="Preferences" className="flex flex-col gap-6">
      {formError && <FormAlert>{formError}</FormAlert>}
      {notice && <FormNotice>{notice}</FormNotice>}
      <div className="grid gap-x-7 gap-y-5 md:grid-cols-2">
        <SelectField
          label="Currency"
          options={CURRENCY_OPTIONS}
          value={values.currency}
          onChange={(event) => change('currency', event.target.value as Currency)}
          error={errors.currency}
        />
        <SelectField
          label="Time Zone"
          options={zones}
          value={values.timeZone}
          onChange={(event) => change('timeZone', event.target.value)}
          error={errors.timeZone}
        />
      </div>
      <fieldset className="flex flex-col gap-4">
        <legend className="mb-4 text-lg font-medium text-title">Notification</legend>
        {NOTIFICATIONS.map(({ name, label }) => (
          <Toggle key={name} label={label} checked={values[name]} onChange={(checked) => change(name, checked)} />
        ))}
      </fieldset>
      <p className="text-sm text-muted">The currency changes how amounts are shown; balances are kept in USD.</p>
      <div className="flex justify-end">
        <PrimaryButton type="submit" disabled={save.isPending} className="w-full md:w-48">
          {save.isPending ? 'Saving…' : 'Save'}
        </PrimaryButton>
      </div>
    </form>
  )
}
