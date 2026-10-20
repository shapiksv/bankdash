import type { CurrentUser } from '../api/auth'
import type { Preferences, Profile } from '../api/profile'

export const jane: CurrentUser = { id: 'u1', email: 'jane@example.com', twoFactorEnabled: false }

export const janeProfile: Profile = {
  userId: 'u1',
  fullName: 'Jane Doe',
  username: 'jane',
  dateOfBirth: null,
  presentAddress: null,
  permanentAddress: null,
  city: null,
  postalCode: null,
  country: null,
  avatarUpdatedAt: null,
}

export const janePreferences: Preferences = {
  currency: 'USD',
  timeZone: 'UTC',
  notifyOnTransfers: true,
  notifyOnMerchantOrders: false,
  notifyOnRecommendations: true,
}
