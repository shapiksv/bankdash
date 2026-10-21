import type { CardStatus } from '../api/cards'

const STATUS_LABELS: Record<CardStatus, string> = {
  INACTIVE: 'Inactive',
  ACTIVE: 'Active',
  BLOCKED: 'Blocked',
  EXPIRED: 'Expired',
}

export function statusLabel(status: CardStatus): string {
  return STATUS_LABELS[status]
}
