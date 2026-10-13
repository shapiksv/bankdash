import { useId, type ButtonHTMLAttributes, type InputHTMLAttributes } from 'react'

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
