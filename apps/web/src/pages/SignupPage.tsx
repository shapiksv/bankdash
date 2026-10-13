import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { MIN_PASSWORD_LENGTH, validateEmail, validateNewPassword, withoutEmpty } from '../auth/validation'
import { FormAlert, PrimaryButton, TextField } from '../components/form'

export function SignupPage() {
  const { state, signup } = useAuth()
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  if (state.status === 'authenticated') {
    return <Navigate to="/" replace />
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const found = withoutEmpty({
      fullName: fullName.trim() ? undefined : 'Enter your full name',
      email: validateEmail(email),
      password: validateNewPassword(password),
    })
    setErrors(found)
    setFormError(undefined)
    if (Object.keys(found).length > 0) return

    setSubmitting(true)
    try {
      await signup({ fullName: fullName.trim(), email: email.trim(), password })
      navigate('/', { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setErrors({ email: 'An account with this email already exists' })
      } else if (error instanceof ApiError && error.status === 400 && error.problem.errors?.length) {
        setErrors(error.fieldErrors())
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section aria-labelledby="signup-title" className="flex flex-col gap-8">
      <header className="space-y-2">
        <h1 id="signup-title" className="text-[22px] font-semibold text-title">
          Create your account
        </h1>
        <p className="text-muted">It takes less than a minute.</p>
      </header>

      <form noValidate onSubmit={onSubmit} className="flex flex-col gap-5">
        {formError && <FormAlert>{formError}</FormAlert>}
        <TextField
          label="Full name"
          autoComplete="name"
          placeholder="Charlene Reed"
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          error={errors.fullName}
        />
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={errors.email}
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="new-password"
          placeholder="Create a password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errors.password}
          hint={`At least ${MIN_PASSWORD_LENGTH} characters`}
        />
        <PrimaryButton type="submit" disabled={submitting} className="mt-2">
          {submitting ? 'Creating account…' : 'Sign up'}
        </PrimaryButton>
      </form>

      <p className="text-center text-muted">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-primary hover:underline">
          Log in
        </Link>
      </p>
    </section>
  )
}
