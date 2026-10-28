import type { Page, Route } from '@playwright/test'
import { removeSavings, restoreSavings, setBalances } from '../support/db.ts'
import { expect, test } from '../support/fixtures.ts'

// specs/BANK-5-balances/spec.md. No API changes balances yet, so they are seeded in account-service's database.

const SUMMARY = '**/api/accounts/summary'
const PENDING_TEXT = 'Setting up your accounts…'
const ERROR_TEXT = 'Your balance could not be loaded.'

/** The two balance cards; AC9–AC12 expect the same state on both. */
const KPIS = ['My Balance', 'Total Saving'] as const

function kpi(page: Page, label: (typeof KPIS)[number]) {
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

async function expectBalances(page: Page, myBalance: string, totalSaving: string, options?: { timeout: number }): Promise<void> {
  await expect(kpi(page, 'My Balance').getByText(myBalance, { exact: true })).toBeVisible(options)
  await expect(kpi(page, 'Total Saving').getByText(totalSaving, { exact: true })).toBeVisible(options)
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
  for (const label of KPIS) {
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
    for (const label of KPIS) {
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

// The polling tests pause Playwright's clock, so only runFor and fastForward move it: slow steps (a database
// write, a loaded machine) cannot fire a poll early or late. The query restarts its 2 s timer whenever a response
// arrives, so each poll is awaited until its response is in before the clock moves on.

/** Installs the clock paused. Call before `goto`. */
async function pauseClock(page: Page): Promise<void> {
  await page.clock.install()
  await page.clock.pauseAt(Date.now() + 1000)
}

/**
 * While the clock is paused, TanStack Query's results reach the UI only through its setTimeout(0): retries
 * `assertion` after running the timers that are due, without moving the clock. Assertions inside use {@link NOW}.
 */
async function whilePaused(page: Page, assertion: () => Promise<void>): Promise<void> {
  await expect(async () => {
    await page.clock.runFor(0)
    await assertion()
  }).toPass({ timeout: 10_000 })
}
const NOW = { timeout: 100 }

/** Moves the paused clock with `move`, which must trigger exactly one poll, and waits for that poll's response. */
async function pollAfter(page: Page, move: () => Promise<void>): Promise<void> {
  const finished = page
    .waitForEvent('requestfinished', {
      predicate: (request) => new URL(request.url()).pathname === '/api/accounts/summary',
      timeout: 10_000,
    })
    .catch(() => {
      throw new Error('Moving the clock started no poll that finished within 10 s')
    })
  await move()
  await finished
  // Lets the page handle the response, which restarts the query's timer.
  await settle(page)
}

test('BANK-5 AC12: polls every 2 s while pending and stops once the summary is READY', async ({ page, user }) => {
  await setBalances(user.id, '1250.50', '300.00')
  const savings = await removeSavings(user.id)
  await pauseClock(page)
  const requests = countSummaryRequests(page)
  await page.goto('/accounts')
  await whilePaused(page, () => expect(kpi(page, 'My Balance')).toContainText(PENDING_TEXT, NOW))
  expect(requests()).toBe(1)

  await page.clock.runFor(1999)
  await settle(page)
  expect(requests(), 'no poll before 2 s').toBe(1)
  await pollAfter(page, () => page.clock.runFor(1))
  expect(requests(), 'a poll at 2 s').toBe(2)
  await whilePaused(page, () => expect(kpi(page, 'My Balance')).toContainText(PENDING_TEXT, NOW))

  await restoreSavings(savings)
  await pollAfter(page, () => page.clock.runFor(2000))
  expect(requests(), 'a poll at 4 s').toBe(3)
  await whilePaused(page, () => expectBalances(page, '$1,250.50', '$300.00', NOW))

  await page.clock.runFor(10_000)
  await settle(page)
  expect(requests(), 'no poll after READY').toBe(3)
})

test('BANK-5 AC12: stops polling after 30 s of PENDING and shows the error state', async ({ page, user }) => {
  await removeSavings(user.id)
  await pauseClock(page)
  const requests = countSummaryRequests(page)
  await page.goto('/accounts')
  await whilePaused(page, () => expect(kpi(page, 'My Balance')).toContainText(PENDING_TEXT, NOW))
  expect(requests()).toBe(1)

  // A jump fires the due poll timer once, so this is a single poll at 28 s.
  await pollAfter(page, () => page.clock.fastForward(28_000))
  expect(requests()).toBe(2)
  await page.clock.runFor(1999)
  await settle(page)
  expect(requests(), 'no poll before 30 s').toBe(2)
  await whilePaused(page, () => expect(kpi(page, 'My Balance'), 'still pending before 30 s').toContainText(PENDING_TEXT, NOW))

  await pollAfter(page, () => page.clock.runFor(1))
  expect(requests(), 'a poll at 30 s').toBe(3)
  await whilePaused(page, async () => {
    for (const label of KPIS) {
      await expect(kpi(page, label), 'the error state at 30 s').toContainText(ERROR_TEXT, NOW)
      await expect(kpi(page, label).getByRole('button', { name: 'Retry' })).toBeVisible(NOW)
    }
  })

  await page.clock.runFor(10_000)
  await settle(page)
  expect(requests(), 'no poll after the limit').toBe(3)
})