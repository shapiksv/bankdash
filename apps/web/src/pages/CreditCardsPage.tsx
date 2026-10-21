import { useState } from 'react'
import type { Card } from '../api/cards'
import { CardTile } from '../cards/CardTile'
import { ConfirmDialog } from '../cards/ConfirmDialog'
import { useBlockCard, useCards } from '../cards/queries'
import { statusLabel } from '../cards/status'
import { FormAlert, FormNotice } from '../components/form'

/**
 * The Credit Cards screen: the user's cards and the selected card's details. Cards are only ever shown masked.
 * A new user's first card is opened asynchronously after signup, so an empty list shows a "being prepared" state.
 */
export function CreditCardsPage() {
  const cards = useCards()
  const [selectedId, setSelectedId] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const list = cards.data ?? []
  const selected = list.find((card) => card.id === selectedId) ?? list[0]

  return (
    <div className="flex flex-col gap-6">
      {notice && <FormNotice>{notice}</FormNotice>}
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
                <CardTile
                  card={card}
                  selected={card.id === selected?.id}
                  onSelect={() => {
                    setSelectedId(card.id)
                    setNotice(undefined)
                  }}
                />
              </li>
            ))}
          </ul>
        )}
      </section>
      {selected && <CardDetails key={selected.id} card={selected} onDone={setNotice} />}
    </div>
  )
}

interface CardDetailsProps {
  card: Card
  /** Reports a completed action, shown as a confirmation message above the cards. */
  onDone: (message: string) => void
}

function CardDetails({ card, onDone }: CardDetailsProps) {
  const ending = card.maskedNumber.slice(-4)
  const block = useBlockCard()
  const [confirmingBlock, setConfirmingBlock] = useState(false)

  return (
    <section aria-label="Card details" className="flex flex-col gap-4 rounded-[25px] bg-surface p-6 lg:p-8">
      <h2 className="text-xl font-semibold text-title">Card ending {ending}</h2>
      {block.isError && <FormAlert>{block.error.message}</FormAlert>}
      <dl className="grid gap-4 sm:grid-cols-2">
        <Detail term="Card Number" value={card.maskedNumber} />
        <Detail term="Card Holder" value={card.holderName} />
        <Detail term="Card Type" value={card.type === 'DEBIT' ? 'Debit' : card.type} />
        <Detail term="Status" value={statusLabel(card.status)} />
        <Detail term="Valid Thru" value={card.expiry} />
        <Detail term="Linked Account" value={card.accountId} />
      </dl>
      {card.status !== 'BLOCKED' && (
        <div className="flex flex-wrap gap-3">
          <button
            type="button"
            onClick={() => setConfirmingBlock(true)}
            className="h-12 rounded-full border border-danger px-6 text-base font-medium text-danger transition hover:bg-danger/10"
          >
            Block card
          </button>
        </div>
      )}
      {confirmingBlock && (
        <ConfirmDialog
          title={`Block card ending ${ending}?`}
          confirmLabel="Confirm"
          busy={block.isPending}
          onCancel={() => setConfirmingBlock(false)}
          onConfirm={() =>
            block.mutate(card.id, {
              onSuccess: () => onDone(`Card ending ${ending} has been blocked`),
              onSettled: () => setConfirmingBlock(false),
            })
          }
        >
          Blocking is final: the card can no longer be used, and it cannot be unblocked.
        </ConfirmDialog>
      )}
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
