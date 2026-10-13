import { Pencil } from 'lucide-react'
import { useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { AVATAR_TYPES, type Profile, type ProfileInput } from '../../api/profile'
import { useAuth } from '../../auth/useAuth'
import { withoutEmpty } from '../../auth/validation'
import { FormAlert, FormNotice, PrimaryButton, TextField } from '../../components/form'
import { initials, useAvatarUrl, useProfile, useUpdateProfile, useUploadAvatar } from '../../profile/queries'
import { todayIso, validateAvatar, validateProfile } from './validation'

export function EditProfileTab() {
  const { data: profile, error, isPending } = useProfile()

  if (isPending) return <p role="status">Loading your profile…</p>
  if (error) {
    return (
      <FormAlert>
        {error instanceof ApiError && error.status === 404
          ? 'We are still setting up your profile. Please try again in a moment.'
          : 'Your profile could not be loaded. Please try again.'}
      </FormAlert>
    )
  }
  // Keyed, so the form starts from the loaded values instead of syncing state in an effect.
  return <EditProfileForm key={profile.userId} profile={profile} />
}

type Values = Record<keyof ProfileInput, string>

const FIELDS: { name: keyof ProfileInput; label: string; type?: string; autoComplete?: string }[] = [
  { name: 'fullName', label: 'Your Name', autoComplete: 'name' },
  { name: 'username', label: 'User Name', autoComplete: 'username' },
  { name: 'dateOfBirth', label: 'Date of Birth', type: 'date', autoComplete: 'bday' },
  { name: 'presentAddress', label: 'Present Address', autoComplete: 'street-address' },
  { name: 'permanentAddress', label: 'Permanent Address' },
  { name: 'city', label: 'City', autoComplete: 'address-level2' },
  { name: 'postalCode', label: 'Postal Code', autoComplete: 'postal-code' },
  { name: 'country', label: 'Country', autoComplete: 'country-name' },
]

function toValues(profile: Profile): Values {
  return Object.fromEntries(FIELDS.map(({ name }) => [name, profile[name] ?? ''])) as Values
}

/** Empty optional fields are sent as null, so they are cleared rather than stored as "". */
function toInput(values: Values): ProfileInput {
  const optional = (value: string) => (value.trim() ? value.trim() : null)
  return {
    fullName: values.fullName.trim(),
    username: values.username.trim(),
    dateOfBirth: values.dateOfBirth || null,
    presentAddress: optional(values.presentAddress),
    permanentAddress: optional(values.permanentAddress),
    city: optional(values.city),
    postalCode: optional(values.postalCode),
    country: optional(values.country),
  }
}

function EditProfileForm({ profile }: { profile: Profile }) {
  const { state } = useAuth()
  const email = state.status === 'authenticated' ? state.user.email : ''
  const [values, setValues] = useState(() => toValues(profile))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const save = useUpdateProfile()

  function edit(field: keyof ProfileInput) {
    return (event: ChangeEvent<HTMLInputElement>) => {
      setValues((current) => ({ ...current, [field]: event.target.value }))
      setErrors(({ [field]: _cleared, ...rest }) => rest)
      setNotice(undefined)
    }
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const input = toInput(values)
    const found = withoutEmpty(validateProfile(input, todayIso()))
    setErrors(found)
    setFormError(undefined)
    setNotice(undefined)
    if (Object.keys(found).length > 0) return

    try {
      setValues(toValues({ ...profile, ...(await save.mutateAsync(input)) }))
      setNotice('Profile saved')
    } catch (error) {
      if (error instanceof ApiError && error.problem.errors?.length) {
        setErrors(error.fieldErrors())
      } else {
        setFormError('Your profile could not be saved. Please try again.')
      }
    }
  }

  return (
    <form noValidate onSubmit={onSubmit} aria-label="Edit Profile" className="flex flex-col gap-8 lg:flex-row lg:gap-14">
      <AvatarEditor profile={profile} email={email} />

      <div className="flex flex-1 flex-col gap-6">
        {formError && <FormAlert>{formError}</FormAlert>}
        {notice && <FormNotice>{notice}</FormNotice>}
        <div className="grid gap-x-7 gap-y-5 md:grid-cols-2">
          {FIELDS.slice(0, 2).map((field) => (
            <TextField key={field.name} {...field} value={values[field.name]} onChange={edit(field.name)} error={errors[field.name]} />
          ))}
          <TextField label="Email" type="email" value={email} readOnly hint="Your login email" />
          {FIELDS.slice(2).map((field) => (
            <TextField key={field.name} {...field} value={values[field.name]} onChange={edit(field.name)} error={errors[field.name]} />
          ))}
        </div>
        <div className="flex justify-end">
          <PrimaryButton type="submit" disabled={save.isPending} className="w-full md:w-48">
            {save.isPending ? 'Saving…' : 'Save'}
          </PrimaryButton>
        </div>
      </div>
    </form>
  )
}

/** Uploads as soon as a file is chosen, independent of the form's Save. */
function AvatarEditor({ profile, email }: { profile: Profile; email: string }) {
  const avatarUrl = useAvatarUrl(profile)
  const upload = useUploadAvatar()
  const input = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string>()

  async function onChoose(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    const invalid = validateAvatar(file)
    setError(invalid)
    if (invalid) return
    try {
      await upload.mutateAsync(file)
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : 'The image could not be uploaded. Please try again.')
    }
  }

  return (
    <div className="flex flex-col items-center gap-3">
      <div className="relative">
        <div className="grid size-28 place-items-center overflow-hidden rounded-full bg-primary/10 text-3xl font-semibold text-primary">
          {avatarUrl ? <img src={avatarUrl} alt="Your avatar" className="size-full object-cover" /> : initials(profile.fullName, email)}
        </div>
        <button
          type="button"
          onClick={() => input.current?.click()}
          disabled={upload.isPending}
          aria-label="Change avatar"
          className="absolute right-0 bottom-1 grid size-8 place-items-center rounded-full bg-primary text-white hover:bg-primary/90 disabled:opacity-60"
        >
          <Pencil className="size-4" aria-hidden />
        </button>
        <input
          ref={input}
          type="file"
          accept={AVATAR_TYPES.join(',')}
          onChange={onChoose}
          className="hidden"
          data-testid="avatar-input"
        />
      </div>
      {upload.isPending && <p role="status" className="text-sm text-muted">Uploading…</p>}
      {error && (
        <p role="alert" className="max-w-40 text-center text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  )
}
