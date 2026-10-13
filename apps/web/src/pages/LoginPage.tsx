import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '../api/client'
import { safeRedirect } from '../auth/redirect'
import { useAuth } from '../auth/useAuth'
import { validateEmail, withoutEmpty } from '../auth/validation'
import { FormAlert, PrimaryButton, TextField } from '../components/form'

export function LoginPage() {
  const { state, login } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)
  const redirectTo = safeRedirect(params.get('from'))

  /** Editing a field clears its error; the rest stay until the next submit. */
  function edit(field: string, setValue: (value: string) => void) {
    return (event: ChangeEvent<HTMLInputElement>) => {
      setValue(event.target.value)
      setErrors(({ [field]: _cleared, ...rest }) => rest)
    }
  }

  if (state.status === 'authenticated') {
    return <Navigate to={redirectTo} replace />
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const found = withoutEmpty({ email: validateEmail(email), password: password ? undefined : 'Enter your password' })
    setErrors(found)
    setFormError(undefined)
    if (Object.keys(found).length > 0) return

    setSubmitting(true)
    try {
      await login(email.trim(), password)
      navigate(redirectTo, { replace: true })
    } catch (error) {
      setFormError(
        error instanceof ApiError && error.status === 401
          ? 'Invalid email or password'
          : 'Something went wrong. Please try again.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section aria-labelledby="login-title" className="flex flex-col gap-8">
      <header className="space-y-2">
        <h1 id="login-title" className="text-[22px] font-semibold text-title">
          Welcome back
        </h1>
        <p className="text-muted">Log in to your BankDash account.</p>
      </header>

      <form noValidate onSubmit={onSubmit} className="flex flex-col gap-5">
        {formError && <FormAlert>{formError}</FormAlert>}
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
          value={email}
          onChange={edit('email', setEmail)}
          error={errors.email}
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          placeholder="Your password"
          value={password}
          onChange={edit('password', setPassword)}
          error={errors.password}
        />
        <PrimaryButton type="submit" disabled={submitting} className="mt-2">
          {submitting ? 'Logging in…' : 'Log in'}
        </PrimaryButton>
      </form>

      <p className="text-center text-muted">
        Don&apos;t have an account?{' '}
        <Link to="/signup" className="font-medium text-primary hover:underline">
          Sign up
        </Link>
      </p>
    </section>
  )
}
