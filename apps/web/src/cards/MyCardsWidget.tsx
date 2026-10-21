import { Link } from 'react-router'
import { CardTile } from './CardTile'
import { useCards } from './queries'

/** Shown on the Dashboard. */
export const DASHBOARD_CARDS = 2

/** The Dashboard's "My Cards": the user's first two cards, masked, with a link to all of them. */
export function MyCardsWidget() {
  const cards = useCards(DASHBOARD_CARDS)
  const list = cards.data ?? []

  return (
    <section aria-labelledby="dashboard-my-cards" className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 id="dashboard-my-cards" className="text-xl font-semibold text-title">
          My Cards
        </h2>
        <Link to="/credit-cards" className="text-base font-semibold text-title hover:text-primary">
          See All
        </Link>
      </div>
      {cards.isError && <p className="text-danger">Your cards could not be loaded.</p>}
      {cards.isSuccess && list.length === 0 && (
        <p className="rounded-[25px] bg-surface p-6 text-muted">Your cards are being prepared.</p>
      )}
      {list.length > 0 && (
        <ul aria-label="My Cards" className="grid gap-6 md:grid-cols-2">
          {list.map((card) => (
            <li key={card.id}>
              <CardTile card={card} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
