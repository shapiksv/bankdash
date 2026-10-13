const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export const MIN_PASSWORD_LENGTH = 8

export function validateEmail(email: string): string | undefined {
  if (!email.trim()) return 'Enter your email'
  if (!EMAIL.test(email.trim())) return 'Enter a valid email address'
  return undefined
}

/** Mirrors the server's PasswordPolicy; the server stays the authority (it also caps the length in bytes). */
export function validateNewPassword(password: string): string | undefined {
  if (!password) return 'Enter a password'
  if ([...password].length < MIN_PASSWORD_LENGTH) return `Use at least ${MIN_PASSWORD_LENGTH} characters`
  if (new TextEncoder().encode(password).length > 72) return 'Password is too long'
  return undefined
}

export function withoutEmpty(errors: Record<string, string | undefined>): Record<string, string> {
  return Object.fromEntries(Object.entries(errors).filter((entry): entry is [string, string] => !!entry[1]))
}
