import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { json } from '../test/fetchMock'
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
})
