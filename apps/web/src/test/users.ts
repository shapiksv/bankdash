import type { CurrentUser } from '../api/auth'
import type { Card } from '../api/cards'
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

const card = (id: string, last4: string, accountId: string, status: Card['status']): Card => ({
  id,
  accountId,
  type: 'DEBIT',
  maskedNumber: `**** **** **** ${last4}`,
  holderName: 'Jane Doe',
  expiry: '10/29',
  status,
})

/** Jane's cards as `GET /api/cards` returns them: one without a PIN yet and two ACTIVE ones. */
export const janeCards: Card[] = [
  card('c1', '5678', 'checking', 'INACTIVE'),
  card('c2', '1234', 'savings', 'ACTIVE'),
  card('c3', '9012', 'checking', 'ACTIVE'),
]
