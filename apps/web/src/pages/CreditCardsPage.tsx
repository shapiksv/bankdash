import { useState } from 'react'
import type { Card } from '../api/cards'
import { CardTile } from '../cards/CardTile'
import { useCards } from '../cards/queries'
import { statusLabel } from '../cards/status'
import { FormAlert } from '../components/form'

/**
 * The Credit Cards screen: the user's cards and the selected card's details. Cards are only ever shown masked.
 * A new user's first card is opened asynchronously after signup, so an empty list shows a "being prepared" state.
 */
export function CreditCardsPage() {
  const cards = useCards()
  const [selectedId, setSelectedId] = useState<string>()
  const list = cards.data ?? []
  const selected = list.find((card) => card.id === selectedId) ?? list[0]

  return (
    <div className="flex flex-col gap-6">
      <section aria-labelledby="my-cards" className="flex flex-col gap-4">
        <h2 id="my-cards" className="text-xl font-semibold text-title">
          My Cards
        </h2>
        {cards.isPending && <p className="text-muted">Loading your cards…</p>}
        {cards.isError && <FormAlert>Your cards could not be loaded. Please try again.</FormAlert>}
        {cards.isSuccess && list.length === 0 && (
          <p className="rounded-[25px] bg-surface p-6 text-muted">
            Your cards are being prepared. They appear here in a moment — refresh the page if they do not.
          </p>
        )}
        {list.length > 0 && (
          <ul aria-label="Cards" className="grid gap-6 md:grid-cols-2 xl:grid-cols-3">
            {list.map((card) => (
              <li key={card.id}>
                <CardTile card={card} selected={card.id === selected?.id} onSelect={() => setSelectedId(card.id)} />
              </li>
            ))}
          </ul>
        )}
      </section>
      {selected && <CardDetails card={selected} />}
    </div>
  )
}

function CardDetails({ card }: { card: Card }) {
  return (
    <section aria-label="Card details" className="flex flex-col gap-4 rounded-[25px] bg-surface p-6 lg:p-8">
      <h2 className="text-xl font-semibold text-title">Card ending {card.maskedNumber.slice(-4)}</h2>
      <dl className="grid gap-4 sm:grid-cols-2">
        <Detail term="Card Number" value={card.maskedNumber} />
        <Detail term="Card Holder" value={card.holderName} />
        <Detail term="Card Type" value={card.type === 'DEBIT' ? 'Debit' : card.type} />
        <Detail term="Status" value={statusLabel(card.status)} />
        <Detail term="Valid Thru" value={card.expiry} />
        <Detail term="Linked Account" value={card.accountId} />
      </dl>
    </section>
  )
}

function Detail({ term, value }: { term: string; value: string }) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="text-sm text-muted">{term}</dt>
      <dd className="break-all text-base text-title">{value}</dd>
    </div>
  )
}
