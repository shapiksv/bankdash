import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
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

describe('Accounts page polling', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('AC12: polls every 2 seconds and stops once READY', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const answers = [pending, pending, ready('1250.50', '300.00')]
    const { api } = renderAccounts(() => json(answers.shift() ?? ready('1250.50', '300.00')))

    expect(await screen.findAllByText('Setting up your accounts…')).toHaveLength(2)
    expect(summaryCalls(api)).toHaveLength(1)

    await vi.advanceTimersByTimeAsync(1500)
    expect(summaryCalls(api)).toHaveLength(1)
    await vi.advanceTimersByTimeAsync(600)
    await vi.waitFor(() => expect(summaryCalls(api)).toHaveLength(2))
    await vi.advanceTimersByTimeAsync(2000)

    expect(await screen.findByText('$1,250.50')).toBeInTheDocument()
    expect(screen.getByText('$300.00')).toBeInTheDocument()
    expect(summaryCalls(api)).toHaveLength(3)

    await vi.advanceTimersByTimeAsync(10_000)
    expect(summaryCalls(api)).toHaveLength(3)
  })

  it('AC12: stops polling after 30 seconds and shows the error', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const { api } = renderAccounts(() => json(pending))
    await screen.findAllByText('Setting up your accounts…')

    await vi.advanceTimersByTimeAsync(29_000)
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument()

    await vi.advanceTimersByTimeAsync(3000)
    expect(await within(card('My Balance')).findByRole('button', { name: 'Retry' })).toBeInTheDocument()
    expect(within(card('Total Saving')).getByRole('button', { name: 'Retry' })).toBeInTheDocument()
    expect(screen.queryByText('Setting up your accounts…')).not.toBeInTheDocument()

    const calls = summaryCalls(api).length
    expect(calls).toBeLessThanOrEqual(17)
    await vi.advanceTimersByTimeAsync(20_000)
    expect(summaryCalls(api)).toHaveLength(calls)
  })

  it('AC12: Retry after the timeout starts polling again', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    let answer: AccountSummary = pending
    const { api } = renderAccounts(() => json(answer))
    await vi.advanceTimersByTimeAsync(32_000)
    await user.click(await within(card('My Balance')).findByRole('button', { name: 'Retry' }))

    expect(await screen.findAllByText('Setting up your accounts…')).toHaveLength(2)
    const afterRetry = summaryCalls(api).length
    answer = ready('7.00', '8.00')
    await vi.advanceTimersByTimeAsync(2500)

    expect(await screen.findByText('$7.00')).toBeInTheDocument()
    expect(summaryCalls(api).length).toBeGreaterThan(afterRetry)
  })
})
