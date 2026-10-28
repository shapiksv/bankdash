import type { Page } from '@playwright/test'
import { last4, PINS, type Card } from '../support/api.ts'
import { expect, test } from '../support/fixtures.ts'

// specs/BANK-4-cards/spec.md. Cards are set up through the real API; only the asserted behaviour goes through the UI.

const MASKED = /^\*{4} \*{4} \*{4} \d{4}$/

/** The masked numbers of the card tiles in a list, in display order. */
async function maskedNumbers(page: Page, listName: string): Promise<string[]> {
  const list = page.getByRole('list', { name: listName, exact: true })
  await expect(list).toBeVisible()
  return list.getByTestId('masked-number').allTextContents()
}

test('BANK-4 AC15: Block card asks for confirmation; Cancel sends nothing, Confirm blocks the card', async ({
  page,
  api,
}) => {
  const [card] = await api.cards()
  const blockRequests: string[] = []
  page.on('request', (request) => {
    if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/block')) blockRequests.push(request.url())
  })
  await page.goto('/credit-cards')
  const details = page.getByRole('region', { name: 'Card details' })
  const dialog = page.getByRole('alertdialog', { name: `Block card ending ${last4(card!)}?` })

  await details.getByRole('button', { name: 'Block card' }).click()
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: 'Cancel' }).click()
  await expect(dialog).toBeHidden()
  expect(blockRequests).toHaveLength(0)
  expect((await api.cards())[0]!.status).toBe('INACTIVE')

  await details.getByRole('button', { name: 'Block card' }).click()
  const blocked = page.waitForResponse((r) => r.request().method() === 'POST' && r.url().endsWith(`/api/cards/${card!.id}/block`))
  await dialog.getByRole('button', { name: 'Confirm' }).click()
  expect((await blocked).status()).toBe(204)
  expect(blockRequests).toHaveLength(1)
  await expect(page.getByRole('status')).toHaveText(`Card ending ${last4(card!)} has been blocked`)
  await expect(details).toContainText('Blocked')
  expect((await api.cards())[0]!.status).toBe('BLOCKED')
})

test.describe('with several cards, two of them blocked', () => {
  // Oldest first: the registration card, then three added ones. The first and third are blocked.
  let first: Card, second: Card, third: Card, fourth: Card

  test.beforeEach(async ({ api }) => {
    ;[first] = await api.cards()
    second = await api.addCard('CHECKING', PINS[0])
    third = await api.addCard('SAVINGS', PINS[1])
    fourth = await api.addCard('CHECKING', PINS[2])
    await api.blockCard(first!.id)
    await api.blockCard(third.id)
  })

  test('BANK-4 AC17 AC20: Credit Cards page lists all cards masked, non-blocked newest first, then blocked newest first', async ({
    page,
  }) => {
    await page.goto('/credit-cards')
    const shown = await maskedNumbers(page, 'Cards')

    expect(shown).toEqual([fourth, second, third, first!].map((card) => card.maskedNumber))
    for (const number of shown) expect(number).toMatch(MASKED)
  })

  test('BANK-4 AC17: Dashboard My Cards shows the 2 newest non-blocked cards, masked', async ({ page }) => {
    await page.goto('/')
    const shown = await maskedNumbers(page, 'My Cards')

    expect(shown).toEqual([fourth, second].map((card) => card.maskedNumber))
    for (const number of shown) expect(number).toMatch(MASKED)
  })
})

test('BANK-4 AC17: Dashboard My Cards never shows a blocked card', async ({ page, api }) => {
  // The API's first 2 are the new card and the blocked registration card; the widget drops the blocked one.
  const [registrationCard] = await api.cards()
  await api.blockCard(registrationCard!.id)
  const added = await api.addCard('SAVINGS', PINS[3])

  await page.goto('/')
  expect(await maskedNumbers(page, 'My Cards')).toEqual([added.maskedNumber])
})