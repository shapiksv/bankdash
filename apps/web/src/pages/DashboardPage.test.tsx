import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { json } from '../test/fetchMock'
import { jane, janeCards, renderWithAuth } from '../test/renderWithAuth'

const authenticated = { status: 'authenticated', user: jane } as const

describe('Dashboard', () => {
  it('AC17: shows at most 2 cards with masked numbers', async () => {
    const { api } = renderWithAuth('/', {
      state: authenticated,
      // The backend applies the limit; answer like it would.
      api: {
        'GET /api/cards': (request) =>
          json({ cards: janeCards.slice(0, Number(new URL(request.url).searchParams.get('limit') ?? 99)) }),
      },
    })

    const list = await screen.findByRole('list', { name: 'My Cards' })
    expect(within(list).getAllByTestId('masked-number').map((n) => n.textContent)).toEqual([
      '**** **** **** 5678',
      '**** **** **** 1234',
    ])
    const cardCalls = api.calls.filter((c) => new URL(c.url).pathname === '/api/cards')
    expect(cardCalls.map((c) => new URL(c.url).search)).toEqual(['?limit=2'])
  })

  it('AC17: shows an empty state before the first card exists', async () => {
    renderWithAuth('/', { state: authenticated })

    expect(await screen.findByText('Your cards are being prepared.')).toBeInTheDocument()
  })

  it('links to the Credit Cards page', async () => {
    const { router } = renderWithAuth('/', { state: authenticated })

    await userEvent.click(screen.getByRole('link', { name: 'See All' }))

    expect(router.state.location.pathname).toBe('/credit-cards')
  })
})
