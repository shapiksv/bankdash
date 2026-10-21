import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Card } from '../api/cards'
import { json, problem } from '../test/fetchMock'
import { jane, janeCards, renderWithAuth } from '../test/renderWithAuth'

const authenticated = { status: 'authenticated', user: jane } as const
const withCards = { 'GET /api/cards': () => json({ cards: janeCards }) }

function details() {
  return within(screen.getByRole('region', { name: 'Card details' }))
}

describe('Credit Cards', () => {
  it('AC17: lists all cards with masked numbers', async () => {
    renderWithAuth('/credit-cards', { state: authenticated, api: withCards })

    const list = await screen.findByRole('list', { name: 'Cards' })
    const numbers = within(list).getAllByTestId('masked-number').map((n) => n.textContent)
    expect(numbers).toEqual(['**** **** **** 5678', '**** **** **** 1234', '**** **** **** 9012'])
    expect(document.body.textContent).not.toMatch(/\d{5}/)
  })

  it('AC17: shows an empty state while cards are being prepared', async () => {
    renderWithAuth('/credit-cards', { state: authenticated })

    expect(await screen.findByText(/Your cards are being prepared/)).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Cards' })).not.toBeInTheDocument()
  })

  it('AC4: shows status, expiry and linked account in the details', async () => {
    renderWithAuth('/credit-cards', { state: authenticated, api: withCards })

    await userEvent.click(await screen.findByRole('button', { name: 'Card ending 1234' }))

    expect(screen.getByRole('button', { name: 'Card ending 1234' })).toHaveAttribute('aria-pressed', 'true')
    expect(details().getByRole('heading')).toHaveTextContent('Card ending 1234')
    expect(details().getByText('Active')).toBeInTheDocument()
    expect(details().getByText('10/29')).toBeInTheDocument()
    expect(details().getByText('savings')).toBeInTheDocument()
    expect(details().getByText('Debit')).toBeInTheDocument()
  })

  it('AC4: opens the first card by default', async () => {
    renderWithAuth('/credit-cards', { state: authenticated, api: withCards })

    expect(await screen.findByRole('region', { name: 'Card details' })).toHaveTextContent('Card ending 5678')
    expect(details().getByText('Inactive')).toBeInTheDocument()
  })

  describe('Block card', () => {
    /** The cards as account-service would return them, updated by the block request. */
    function blockableCards() {
      let cards: Card[] = janeCards.map((card) => ({ ...card }))
      return {
        'GET /api/cards': () => json({ cards }),
        'POST /api/cards/c2/block': () => {
          cards = cards.map((card) => (card.id === 'c2' ? { ...card, status: 'BLOCKED' as const } : card))
          return new Response(null, { status: 204 })
        },
      }
    }

    async function openBlockDialog() {
      await userEvent.click(await screen.findByRole('button', { name: 'Card ending 1234' }))
      await userEvent.click(details().getByRole('button', { name: 'Block card' }))
      return screen.getByRole('alertdialog', { name: 'Block card ending 1234?' })
    }

    const blockCalls = (calls: Request[]) => calls.filter((c) => c.method === 'POST')

    it('AC15: sends the block request only after Confirm', async () => {
      const { api } = renderWithAuth('/credit-cards', { state: authenticated, api: blockableCards() })

      const dialog = await openBlockDialog()
      expect(dialog).toHaveTextContent('cannot be unblocked')
      expect(blockCalls(api.calls)).toHaveLength(0)

      await userEvent.click(within(dialog).getByRole('button', { name: 'Confirm' }))

      expect(blockCalls(api.calls).map((c) => new URL(c.url).pathname)).toEqual(['/api/cards/c2/block'])
    })

    it('AC15: Cancel sends no request', async () => {
      const { api } = renderWithAuth('/credit-cards', { state: authenticated, api: blockableCards() })

      const dialog = await openBlockDialog()
      expect(within(dialog).getByRole('button', { name: 'Cancel' })).toHaveFocus()
      await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }))

      expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
      expect(blockCalls(api.calls)).toHaveLength(0)
    })

    it('AC15: Escape closes the dialog without blocking', async () => {
      const { api } = renderWithAuth('/credit-cards', { state: authenticated, api: blockableCards() })

      await openBlockDialog()
      await userEvent.keyboard('{Escape}')

      expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
      expect(blockCalls(api.calls)).toHaveLength(0)
    })

    it('AC13: shows the card as blocked and a confirmation message', async () => {
      renderWithAuth('/credit-cards', { state: authenticated, api: blockableCards() })

      const dialog = await openBlockDialog()
      await userEvent.click(within(dialog).getByRole('button', { name: 'Confirm' }))

      expect(await screen.findByRole('status')).toHaveTextContent('Card ending 1234 has been blocked')
      expect(await details().findByText('Blocked')).toBeInTheDocument()
      expect(details().queryByRole('button', { name: 'Block card' })).not.toBeInTheDocument()
    })

    it('AC14: shows the error when the card was already blocked', async () => {
      renderWithAuth('/credit-cards', {
        state: authenticated,
        api: {
          ...withCards,
          'POST /api/cards/c2/block': () =>
            problem(409, { title: 'Card already blocked', detail: 'The card is already blocked' }),
        },
      })

      const dialog = await openBlockDialog()
      await userEvent.click(within(dialog).getByRole('button', { name: 'Confirm' }))

      expect(await details().findByRole('alert')).toHaveTextContent('The card is already blocked')
    })
  })
})
