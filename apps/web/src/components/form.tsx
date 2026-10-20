import { useId, type ButtonHTMLAttributes, type InputHTMLAttributes, type SelectHTMLAttributes } from 'react'

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
  hint?: string
}

/** Pill-shaped input in the style of the Dashboard's Quick Transfer amount field. */
export function TextField({ label, error, hint, className = '', ...input }: TextFieldProps) {
  const id = useId()
  const messageId = `${id}-message`
  const message = error ?? hint
  return (
    <div className={`flex flex-col gap-2 ${className}`}>
      <label htmlFor={id} className="text-base text-title">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={message ? messageId : undefined}
        className={`h-12 rounded-full bg-field px-6 text-base text-title outline-none transition placeholder:text-muted focus:ring-2 focus:ring-primary/40 ${
          error ? 'ring-2 ring-danger/60' : ''
        }`}
        {...input}
      />
      {message && (
        <p id={messageId} className={`px-6 text-sm ${error ? 'text-danger' : 'text-muted'}`}>
          {message}
        </p>
      )}
    </div>
  )
}

export function PrimaryButton({ className = '', ...button }: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      className={`h-12 rounded-full bg-primary px-8 text-base font-medium text-white shadow-button transition hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60 ${className}`}
      {...button}
    />
  )
}

/** A form-level error such as wrong credentials. */
export function FormAlert({ children }: { children: string }) {
  return (
    <p role="alert" className="rounded-2xl bg-danger/10 px-6 py-3 text-sm text-danger">
      {children}
    </p>
  )
}

/** A form-level success message, e.g. after saving. */
export function FormNotice({ children }: { children: string }) {
  return (
    <p role="status" className="rounded-2xl bg-success/10 px-6 py-3 text-sm text-title">
      {children}
    </p>
  )
}

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string
  options: readonly { value: string; label: string }[]
  error?: string
}

/** A native select in the same pill style as {@link TextField}. */
export function SelectField({ label, options, error, className = '', ...select }: SelectFieldProps) {
  const id = useId()
  const messageId = `${id}-message`
  return (
    <div className={`flex flex-col gap-2 ${className}`}>
      <label htmlFor={id} className="text-base text-title">
        {label}
      </label>
      <select
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? messageId : undefined}
        className={`h-12 rounded-full bg-field px-6 text-base text-title outline-none transition focus:ring-2 focus:ring-primary/40 ${
          error ? 'ring-2 ring-danger/60' : ''
        }`}
        {...select}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {error && (
        <p id={messageId} className="px-6 text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  )
}

interface ToggleProps {
  label: string
  checked: boolean
  onChange: (checked: boolean) => void
  disabled?: boolean
}

/** The on/off switch of the Settings mockup, as an accessible switch button. */
export function Toggle({ label, checked, onChange, disabled }: ToggleProps) {
  const id = useId()
  return (
    <div className="flex items-center gap-4">
      <button
        type="button"
        role="switch"
        id={id}
        aria-checked={checked}
        disabled={disabled}
        onClick={() => onChange(!checked)}
        className={`relative h-8 w-14 shrink-0 rounded-full transition disabled:opacity-60 ${
          checked ? 'bg-success' : 'bg-field'
        }`}
      >
        <span
          aria-hidden
          className={`absolute top-1 size-6 rounded-full bg-white shadow transition-all ${checked ? 'left-7' : 'left-1'}`}
        />
      </button>
      <label htmlFor={id} className="text-base text-title">
        {label}
      </label>
    </div>
  )
}
