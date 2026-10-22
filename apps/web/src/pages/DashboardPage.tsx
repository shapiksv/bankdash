import { MyCardsWidget } from '../cards/MyCardsWidget'
import { EmptyPage } from './EmptyPage'

export function DashboardPage() {
  return (
    <div className="flex flex-col gap-6">
      <MyCardsWidget />
      <EmptyPage name="Dashboard" />
    </div>
  )
}
