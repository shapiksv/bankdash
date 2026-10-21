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

/** Waits until the cards are loaded and the first one's details are shown. */
async function loadedDetails() {
  return within(await screen.findByRole('region', { name: 'Card details' }))
}

describe('Credit Cards', () => {
  it('AC17: lists all cards with masked numbers', async () => {
    renderWithAuth('/credit-cards', { state: authenticated, api: withCards })

    const list = await screen.findByRole('list', { name: 'Cards' })
    const numbers = within(list).getAllByTestId('masked-number').map((n) => n.textContent)
    expect(numbers).toEqual(['**** **** **** 5678', '**** **** **** 1234', '**** **** **** 9012'])
    expect(document.body.textContent).not.toMatch(/\d{5}/)
  })

  it('AC20: lists the cards in the order the API returns them and opens the first one', async () => {
    // The API's order: non-blocked newest first, then blocked.
    const ordered: Card[] = [janeCards[2], janeCards[0], { ...janeCards[1], status: 'BLOCKED' }]
    renderWithAuth('/credit-cards', {
      state: authenticated,
      api: { 'GET /api/cards': () => json({ cards: ordered }) },
    })

    const list = await screen.findByRole('list', { name: 'Cards' })
    expect(within(list).getAllByTestId('masked-number').map((n) => n.textContent)).toEqual([
      '**** **** **** 9012',
      '**** **** **** 5678',
      '**** **** **** 1234',
    ])
    expect((await loadedDetails()).getByText('**** **** **** 9012')).toBeInTheDocument()
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

  describe('PIN', () => {
    function pinApi(status = 204, body: Record<string, unknown> = {}) {
      let cards: Card[] = janeCards.map((card) => ({ ...card }))
      return {
        'GET /api/cards': () => json({ cards }),
        'PUT /api/cards/c1/pin': () => {
          if (status !== 204) return problem(status, body)
          cards = cards.map((card) => (card.id === 'c1' ? { ...card, status: 'ACTIVE' as const } : card))
          return new Response(null, { status: 204 })
        },
        'PUT /api/cards/c2/pin': () => (status === 204 ? new Response(null, { status: 204 }) : problem(status, body)),
      }
    }

    const pinCalls = (calls: Request[]) => calls.filter((c) => c.method === 'PUT')

    it('AC8: offers Set PIN for an inactive card and shows it active after saving', async () => {
      const { api } = renderWithAuth('/credit-cards', { state: authenticated, api: pinApi() })

      expect((await loadedDetails()).getByText('Set a PIN to activate this card.')).toBeInTheDocument()
      await userEvent.click(details().getByRole('button', { name: 'Set PIN' }))
      const dialog = screen.getByRole('dialog', { name: 'Set PIN' })
      await userEvent.type(within(dialog).getByLabelText('New PIN'), '4826')
      await userEvent.click(within(dialog).getByRole('button', { name: 'Save PIN' }))

      expect(await screen.findByRole('status')).toHaveTextContent('PIN set. The card is now active.')
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(await pinCalls(api.calls)[0].json()).toEqual({ pin: '4826' })
      expect(await details().findByRole('button', { name: 'Change PIN' })).toBeInTheDocument()
    })

    it('AC9: changes the PIN of an active card', async () => {
      const { api } = renderWithAuth('/credit-cards', { state: authenticated, api: pinApi() })

      await userEvent.click(await screen.findByRole('button', { name: 'Card ending 1234' }))
      await userEvent.click(details().getByRole('button', { name: 'Change PIN' }))
      const dialog = screen.getByRole('dialog', { name: 'Change PIN' })
      await userEvent.type(within(dialog).getByLabelText('New PIN'), '7391')
      await userEvent.click(within(dialog).getByRole('button', { name: 'Save PIN' }))

      expect(await screen.findByRole('status')).toHaveTextContent('PIN changed')
      expect(new URL(pinCalls(api.calls)[0].url).pathname).toBe('/api/cards/c2/pin')
    })

    it('AC10: blocks a weak PIN before sending', async () => {
      const { api } = renderWithAuth('/credit-cards', { state: authenticated, api: pinApi() })

      await userEvent.click((await loadedDetails()).getByRole('button', { name: 'Set PIN' }))
      const dialog = screen.getByRole('dialog', { name: 'Set PIN' })
      await userEvent.type(within(dialog).getByLabelText('New PIN'), '1234')
      await userEvent.click(within(dialog).getByRole('button', { name: 'Save PIN' }))

      expect(within(dialog).getByText('PIN must not be a sequence of consecutive digits')).toBeInTheDocument()
      expect(within(dialog).getByLabelText('New PIN')).toHaveAttribute('aria-invalid', 'true')
      expect(pinCalls(api.calls)).toHaveLength(0)
    })

    it('AC10: shows the backend rule detail on 422', async () => {
      renderWithAuth('/credit-cards', {
        state: authenticated,
        api: pinApi(422, {
          title: 'Weak PIN',
          detail: 'New PIN must differ from the current PIN',
          rule: 'SAME_AS_CURRENT',
        }),
      })

      await userEvent.click(await screen.findByRole('button', { name: 'Card ending 1234' }))
      await userEvent.click(details().getByRole('button', { name: 'Change PIN' }))
      const dialog = screen.getByRole('dialog', { name: 'Change PIN' })
      await userEvent.type(within(dialog).getByLabelText('New PIN'), '7391')
      await userEvent.click(within(dialog).getByRole('button', { name: 'Save PIN' }))

      expect(await within(dialog).findByRole('alert')).toHaveTextContent('New PIN must differ from the current PIN')
      expect(screen.queryByRole('status')).not.toBeInTheDocument()
    })

    it('AC12: offers no PIN change for a blocked or expired card', async () => {
      const cards = [
        { ...janeCards[0], status: 'BLOCKED' as const },
        { ...janeCards[1], status: 'EXPIRED' as const },
      ]
      renderWithAuth('/credit-cards', { state: authenticated, api: { 'GET /api/cards': () => json({ cards }) } })

      expect((await loadedDetails()).getByText('Blocked')).toBeInTheDocument()
      expect(details().queryByRole('button', { name: /PIN/ })).not.toBeInTheDocument()
      await userEvent.click(screen.getByRole('button', { name: 'Card ending 1234' }))
      expect(details().getByText('Expired')).toBeInTheDocument()
      expect(details().queryByRole('button', { name: /PIN/ })).not.toBeInTheDocument()
    })
  })

  describe('Add card', () => {
    it('AC6: adds a card to the chosen account', async () => {
      const added: Card = { ...janeCards[1], id: 'c4', maskedNumber: '**** **** **** 4444' }
      let cards = janeCards
      const { api } = renderWithAuth('/credit-cards', {
        state: authenticated,
        api: {
          'GET /api/cards': () => json({ cards }),
          'POST /api/cards': () => {
            cards = [...janeCards, added]
            return json(added, 201)
          },
        },
      })

      await userEvent.click(await screen.findByRole('button', { name: 'Add card' }))
      const dialog = screen.getByRole('dialog', { name: 'Add card' })
      await userEvent.selectOptions(within(dialog).getByLabelText('Account'), 'Savings')
      await userEvent.type(within(dialog).getByLabelText('PIN'), '4826')
      await userEvent.click(within(dialog).getByRole('button', { name: 'Add card' }))

      expect(await screen.findByRole('status')).toHaveTextContent('Card ending 4444 added')
      const post = api.calls.find((c) => c.method === 'POST')
      expect(await post?.json()).toEqual({ accountType: 'SAVINGS', pin: '4826' })
      expect(await screen.findByRole('button', { name: 'Card ending 4444' })).toHaveAttribute('aria-pressed', 'true')
    })

    it('AC10: does not send a weak PIN', async () => {
      const { api } = renderWithAuth('/credit-cards', { state: authenticated, api: withCards })

      await userEvent.click(await screen.findByRole('button', { name: 'Add card' }))
      const dialog = screen.getByRole('dialog', { name: 'Add card' })
      await userEvent.type(within(dialog).getByLabelText('PIN'), '0000')
      await userEvent.click(within(dialog).getByRole('button', { name: 'Add card' }))

      expect(within(dialog).getByText('PIN must not repeat one digit')).toBeInTheDocument()
      expect(api.calls.filter((c) => c.method === 'POST')).toHaveLength(0)
    })

    it('AC16: shows the card limit error', async () => {
      renderWithAuth('/credit-cards', {
        state: authenticated,
        api: {
          ...withCards,
          'POST /api/cards': () =>
            problem(409, { title: 'Card limit reached', detail: 'At most 5 cards that are not blocked are allowed' }),
        },
      })

      await userEvent.click(await screen.findByRole('button', { name: 'Add card' }))
      const dialog = screen.getByRole('dialog', { name: 'Add card' })
      await userEvent.type(within(dialog).getByLabelText('PIN'), '4826')
      await userEvent.click(within(dialog).getByRole('button', { name: 'Add card' }))

      expect(await within(dialog).findByRole('alert')).toHaveTextContent('At most 5 cards that are not blocked are allowed')
    })
  })
})
