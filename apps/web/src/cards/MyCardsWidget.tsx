import { Link } from 'react-router'
import { CardTile } from './CardTile'
import { useCards } from './queries'

/** Shown on the Dashboard. */
export const DASHBOARD_CARDS = 2

/**
 * The Dashboard's "My Cards": the user's two newest non-blocked cards, masked, with a link to all of them. The API
 * lists non-blocked cards first, so a blocked one only comes back when fewer than two are left; it is left out.
 */
export function MyCardsWidget() {
  const cards = useCards(DASHBOARD_CARDS)
  const all = cards.data ?? []
  const list = all.filter((card) => card.status !== 'BLOCKED')

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
      {cards.isSuccess && all.length === 0 && (
        <p className="rounded-[25px] bg-surface p-6 text-muted">Your cards are being prepared.</p>
      )}
      {all.length > 0 && list.length === 0 && (
        <p className="rounded-[25px] bg-surface p-6 text-muted">All your cards are blocked.</p>
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
