import { apiBlob, apiFetch } from './client'

export interface Profile {
  userId: string
  fullName: string
  username: string
  /** ISO date, e.g. 1990-01-25. */
  dateOfBirth: string | null
  presentAddress: string | null
  permanentAddress: string | null
  city: string | null
  postalCode: string | null
  country: string | null
  /** Null without an avatar; changes with every upload. */
  avatarUpdatedAt: string | null
}

export type ProfileInput = Omit<Profile, 'userId' | 'avatarUpdatedAt'>

export const CURRENCIES = ['USD', 'EUR', 'GBP'] as const
export type Currency = (typeof CURRENCIES)[number]

export interface Preferences {
  currency: Currency
  timeZone: string
  notifyOnTransfers: boolean
  notifyOnMerchantOrders: boolean
  notifyOnRecommendations: boolean
}

export const AVATAR_TYPES = ['image/png', 'image/jpeg', 'image/webp']
export const MAX_AVATAR_BYTES = 1024 * 1024

export function fetchProfile(): Promise<Profile> {
  return apiFetch<Profile>('/profile/me')
}

export function updateProfile(input: ProfileInput): Promise<Profile> {
  return apiFetch<Profile>('/profile/me', { method: 'PUT', body: JSON.stringify(input) })
}

export function fetchAvatar(): Promise<Blob> {
  return apiBlob('/profile/me/avatar')
}

/** The raw file is the body; profile-service checks its type and size again. */
export function uploadAvatar(file: File): Promise<void> {
  return apiFetch<void>('/profile/me/avatar', { method: 'PUT', body: file, headers: { 'Content-Type': file.type } })
}

export function fetchPreferences(): Promise<Preferences> {
  return apiFetch<Preferences>('/preferences')
}

export function updatePreferences(preferences: Preferences): Promise<Preferences> {
  return apiFetch<Preferences>('/preferences', { method: 'PUT', body: JSON.stringify(preferences) })
}
