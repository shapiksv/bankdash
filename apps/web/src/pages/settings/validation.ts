import { AVATAR_TYPES, MAX_AVATAR_BYTES, type ProfileInput } from '../../api/profile'

/** Mirrors profile-service's rules; the server stays the authority. */
export const USERNAME = /^[A-Za-z0-9._-]{3,30}$/

export function validateProfile(input: ProfileInput, today: string): Record<string, string | undefined> {
  return {
    fullName: input.fullName.trim() ? undefined : 'Enter your name',
    username: USERNAME.test(input.username.trim()) ? undefined : 'Use 3 to 30 letters, digits, dots, dashes or underscores',
    // ISO dates compare correctly as strings.
    dateOfBirth: input.dateOfBirth && input.dateOfBirth >= today ? 'Must be in the past' : undefined,
  }
}

export function validateAvatar(file: File): string | undefined {
  if (!AVATAR_TYPES.includes(file.type)) return 'Choose a PNG, JPEG or WebP image'
  if (file.size > MAX_AVATAR_BYTES) return 'The image must be at most 1 MB'
  return undefined
}

/** Today in the browser's time zone, as an ISO date. */
export function todayIso(now = new Date()): string {
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 10)
}
