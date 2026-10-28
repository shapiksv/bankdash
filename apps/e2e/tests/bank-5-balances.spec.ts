import type { Page, Route } from '@playwright/test'
import { removeSavings, restoreSavings, setBalances } from '../support/db.ts'
import { expect, test } from '../support/fixtures.ts'

// specs/BANK-5-balances/spec.md. No API changes balances yet, so they are seeded in account-service's database.

const SUMMARY = '**/api/accounts/summary'
const PENDING_TEXT = 'Setting up your accounts…'
const ERROR_TEXT = 'Your balance could not be loaded.'

function kpi(page: Page, label: 'My Balance' | 'Total Saving') {
  return page.getByRole('region', { name: label })
}

/** Counts the summary requests the page sends from now on. */
function countSummaryRequests(page: Page): () => number {
  let count = 0
  page.on('request', (request) => {
    if (new URL(request.url()).pathname === '/api/accounts/summary') count++
  })
  return () => count
}

/** Gives a request that is not expected a moment to show up before asserting it did not. */
async function settle(page: Page): Promise<void> {
  await page.waitForTimeout(300)
}

async function expectBalances(page: Page, myBalance: string, totalSaving: string): Promise<void> {
  await expect(kpi(page, 'My Balance').getByText(myBalance, { exact: true })).toBeVisible()
  await expect(kpi(page, 'Total Saving').getByText(totalSaving, { exact: true })).toBeVisible()
}

test('BANK-5 AC7: My Balance and Total Saving show the balances in USD', async ({ page, user }) => {
  await setBalances(user.id, '1250.50', '300.00')
  await page.goto('/accounts')
  await expectBalances(page, '$1,250.50', '$300.00')
})

test('BANK-5 AC8: a very large balance is shown exactly', async ({ page, user }) => {
  await setBalances(user.id, '12345678901234.89', '0.10')
  await page.goto('/accounts')
  await expectBalances(page, '$12,345,678,901,234.89', '$0.10')
})

test('BANK-5 AC9 AC11: with only the CHECKING account both cards show the pending state', async ({ page, user }) => {
  await removeSavings(user.id)
  await page.goto('/accounts')
  for (const label of ['My Balance', 'Total Saving'] as const) {
    await expect(kpi(page, label)).toContainText(PENDING_TEXT)
    await expect(kpi(page, label)).not.toContainText(ERROR_TEXT)
  }
})

const FAILURES: [string, (route: Route) => Promise<void>][] = [
  [
    'a 5xx response',
    (route) =>
      route.fulfill({ status: 503, contentType: 'application/problem+json', body: '{"title":"Service Unavailable","status":503}' }),
  ],
  ['a network error', (route) => route.abort('failed')],
]

for (const [failure, fail] of FAILURES) {
  test(`BANK-5 AC10: after ${failure} both cards show an error, and Retry requests the summary again`, async ({
    page,
    user,
  }) => {
    await setBalances(user.id, '1250.50', '300.00')
    // Fault injection only: the summary itself comes from the real backend once the route is removed.
    await page.route(SUMMARY, fail)
    await page.goto('/accounts')
    for (const label of ['My Balance', 'Total Saving'] as const) {
      await expect(kpi(page, label)).toContainText(ERROR_TEXT)
      await expect(kpi(page, label).getByRole('button', { name: 'Retry' })).toBeVisible()
    }

    await page.unroute(SUMMARY)
    const requests = countSummaryRequests(page)
    await kpi(page, 'My Balance').getByRole('button', { name: 'Retry' }).click()
    await expectBalances(page, '$1,250.50', '$300.00')
    expect(requests()).toBeGreaterThanOrEqual(1)
  })
}

// Playwright's clock keeps running in real time after install(); fastForward jumps over the 2 s and 30 s timers.
// It is not paused, because TanStack Query delivers results through setTimeout(0). Since real time also moves
// the clock, every wait right after a fastForward is SOON: short enough that real time alone cannot reach the
// next timer, so a wrong interval or limit fails instead of being waited out.
const SOON = { timeout: 500 }

test('BANK-5 AC12: polls every 2 s while pending and stops once the summary is READY', async ({ page, user }) => {
  await setBalances(user.id, '1250.50', '300.00')
  const savings = await removeSavings(user.id)
  await page.clock.install()
  const requests = countSummaryRequests(page)
  await page.goto('/accounts')
  await expect(kpi(page, 'My Balance')).toContainText(PENDING_TEXT)
  await expect.poll(requests).toBe(1)

  await page.clock.fastForward(1000)
  await settle(page)
  expect(requests(), 'no poll before 2 s').toBe(1)
  await page.clock.fastForward(1000)
  await expect.poll(requests, { message: 'a poll at 2 s', ...SOON }).toBe(2)
  await expect(kpi(page, 'My Balance')).toContainText(PENDING_TEXT)

  await restoreSavings(savings)
  await page.clock.fastForward(2000)
  await expect.poll(requests, { message: 'a poll at 4 s', ...SOON }).toBe(3)
  await expectBalances(page, '$1,250.50', '$300.00')

  await page.clock.fastForward(10_000)
  await settle(page)
  expect(requests(), 'no poll after READY').toBe(3)
})

test('BANK-5 AC12: stops polling after 30 s of PENDING and shows the error state', async ({ page, user }) => {
  await removeSavings(user.id)
  await page.clock.install()
  const requests = countSummaryRequests(page)
  await page.goto('/accounts')
  await expect(kpi(page, 'My Balance')).toContainText(PENDING_TEXT)
  await expect.poll(requests).toBe(1)

  await page.clock.fastForward(28_000)
  await expect.poll(requests, SOON).toBe(2)
  await settle(page)
  await expect(kpi(page, 'My Balance'), 'still pending before 30 s').toContainText(PENDING_TEXT)

  await page.clock.fastForward(2000)
  await expect.poll(requests, SOON).toBe(3)
  for (const label of ['My Balance', 'Total Saving'] as const) {
    await expect(kpi(page, label), 'the error state at 30 s').toContainText(ERROR_TEXT, SOON)
    await expect(kpi(page, label).getByRole('button', { name: 'Retry' })).toBeVisible()
  }

  await page.clock.fastForward(10_000)
  await settle(page)
  expect(requests(), 'no poll after the limit').toBe(3)
})