import { afterEach, describe, expect, it, vi } from 'vitest'
import { json, mockFetch } from '../test/fetchMock'
import { addCard, blockCard, fetchCards, setCardPin } from './cards'
import { janeCards } from '../test/users'

describe('cards API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('AC17: asks for a limited number of cards', async () => {
    const { calls } = mockFetch({ 'GET /api/cards': () => json({ cards: janeCards.slice(0, 2) }) })

    await expect(fetchCards(2)).resolves.toHaveLength(2)
    expect(new URL(calls[0].url).search).toBe('?limit=2')
  })

  it('AC3: fetches all cards without a limit', async () => {
    const { calls } = mockFetch({ 'GET /api/cards': () => json({ cards: janeCards }) })

    await expect(fetchCards()).resolves.toEqual(janeCards)
    expect(new URL(calls[0].url).search).toBe('')
  })

  it('AC6: adds a card with accountType and pin', async () => {
    const { calls } = mockFetch({ 'POST /api/cards': () => json(janeCards[1], 201) })

    await addCard({ accountType: 'SAVINGS', pin: '4826' })

    expect(await calls[0].json()).toEqual({ accountType: 'SAVINGS', pin: '4826' })
  })

  it('AC8: sets the PIN with PUT and AC13: blocks with POST', async () => {
    const noContent = () => new Response(null, { status: 204 })
    const { calls } = mockFetch({ 'PUT /api/cards/c1/pin': noContent, 'POST /api/cards/c1/block': noContent })

    await setCardPin('c1', '4826')
    await blockCard('c1')

    expect(await calls[0].json()).toEqual({ pin: '4826' })
    expect(calls[1].method).toBe('POST')
  })
})
