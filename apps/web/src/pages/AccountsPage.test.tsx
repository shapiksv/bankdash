import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { AccountSummary } from '../api/accounts'
import { json, problem } from '../test/fetchMock'
import { jane, renderWithAuth } from '../test/renderWithAuth'

const authenticated = { status: 'authenticated', user: jane } as const

const ready = (myBalance: string, totalSaving: string): AccountSummary => ({
  status: 'READY',
  currency: 'USD',
  myBalance,
  totalSaving,
})

const pending: AccountSummary = { status: 'PENDING', currency: 'USD', myBalance: null, totalSaving: null }

const card = (name: string) => screen.getByRole('region', { name })

function renderAccounts(summary: () => Response | Promise<Response>) {
  return renderWithAuth('/accounts', { state: authenticated, api: { 'GET /api/accounts/summary': summary } })
}

const summaryCalls = (api: { calls: Request[] }) =>
  api.calls.filter((c) => new URL(c.url).pathname === '/api/accounts/summary')

describe('Accounts page', () => {
  it('shows a loading placeholder until the first response', async () => {
    let answer: (response: Response) => void = () => {}
    renderAccounts(() => new Promise<Response>((resolve) => (answer = resolve)))

    expect(within(card('My Balance')).getByRole('status')).toHaveAttribute('aria-busy', 'true')
    expect(within(card('Total Saving')).getByRole('status')).toBeInTheDocument()
    expect(screen.queryByText('Setting up your accounts…')).not.toBeInTheDocument()

    answer(json(ready('1.00', '2.00')))
    expect(await screen.findByText('$1.00')).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('AC7: shows the balances in USD', async () => {
    renderAccounts(() => json(ready('1250.50', '300.00')))

    expect(await within(card('My Balance')).findByText('$1,250.50')).toBeInTheDocument()
    expect(within(card('Total Saving')).getByText('$300.00')).toBeInTheDocument()
  })

  it('AC8: shows a very large balance exactly', async () => {
    renderAccounts(() => json(ready('12345678901234.89', '0.10')))

    expect(await within(card('My Balance')).findByText('$12,345,678,901,234.89')).toBeInTheDocument()
    expect(within(card('Total Saving')).getByText('$0.10')).toBeInTheDocument()
  })

  it('AC9: shows that the accounts are being set up', async () => {
    renderAccounts(() => json(pending))

    expect(await within(card('My Balance')).findByText('Setting up your accounts…')).toBeInTheDocument()
    expect(within(card('Total Saving')).getByText('Setting up your accounts…')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument()
    expect(screen.queryByText(/could not be loaded/)).not.toBeInTheDocument()
  })

  it('AC10: shows an error with Retry on a 5xx response', async () => {
    renderAccounts(() => problem(503))

    expect(await within(card('My Balance')).findByRole('button', { name: 'Retry' })).toBeInTheDocument()
    expect(within(card('Total Saving')).getByRole('button', { name: 'Retry' })).toBeInTheDocument()
    expect(screen.getAllByText('Your balance could not be loaded.')).toHaveLength(2)
  })

  it('AC10: shows an error with Retry on a network error', async () => {
    renderAccounts(() => {
      throw new TypeError('Failed to fetch')
    })

    expect(await within(card('My Balance')).findByRole('button', { name: 'Retry' })).toBeInTheDocument()
    expect(within(card('Total Saving')).getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('AC10: Retry requests the summary again', async () => {
    let failing = true
    const { api } = renderAccounts(() => (failing ? problem(500) : json(ready('5.00', '6.00'))))
    const retry = await within(card('My Balance')).findByRole('button', { name: 'Retry' })
    const before = summaryCalls(api).length
    failing = false

    await userEvent.click(retry)

    expect(await within(card('My Balance')).findByText('$5.00')).toBeInTheDocument()
    expect(within(card('Total Saving')).getByText('$6.00')).toBeInTheDocument()
    expect(summaryCalls(api).length).toBeGreaterThan(before)
  })
})
