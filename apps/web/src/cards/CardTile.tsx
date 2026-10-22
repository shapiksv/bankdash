import type { Card } from '../api/cards'
import { statusLabel } from './status'

interface CardTileProps {
  card: Card
  selected?: boolean
  onSelect?: () => void
}

/**
 * A card in the blue gradient of the mockup's "My Cards". Only the masked number is ever shown. With onSelect it is
 * a button that opens the card's details.
 */
export function CardTile({ card, selected = false, onSelect }: CardTileProps) {
  const unusable = card.status === 'BLOCKED' || card.status === 'EXPIRED'
  const content = (
    <>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs text-white/70">Card Holder</p>
          <p className="text-base font-semibold">{card.holderName}</p>
        </div>
        <span className="rounded-full bg-white/20 px-3 py-1 text-xs font-medium">{statusLabel(card.status)}</span>
      </div>
      <div>
        <p className="text-xs text-white/70">Valid Thru</p>
        <p className="text-base font-semibold">{card.expiry}</p>
      </div>
      <p className="text-xl font-semibold tracking-wider" data-testid="masked-number">
        {card.maskedNumber}
      </p>
    </>
  )
  const className = `flex h-56 w-full flex-col justify-between rounded-[25px] bg-gradient-to-br from-card-from to-card-to p-6 text-left text-white transition ${
    unusable ? 'opacity-60 grayscale' : ''
  } ${selected ? 'ring-4 ring-primary/40' : ''}`

  if (!onSelect) {
    return <div className={className}>{content}</div>
  }
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      aria-label={`Card ending ${card.maskedNumber.slice(-4)}`}
      className={className}
    >
      {content}
    </button>
  )
}
