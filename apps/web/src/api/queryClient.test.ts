import { describe, expect, it } from 'vitest'
import { ApiError } from './client'
import { retryDelay, shouldRetry } from './queryClient'

describe('shouldRetry', () => {
  it('retries a 404 five times, for the profile that is still being created', () => {
    const notFound = new ApiError(404, { status: 404 })

    expect([0, 1, 2, 3, 4].map((count) => shouldRetry(count, notFound))).toEqual([true, true, true, true, true])
    expect(shouldRetry(5, notFound)).toBe(false)
  })

  it('does not retry other client errors', () => {
    expect(shouldRetry(0, new ApiError(400, {}))).toBe(false)
    expect(shouldRetry(0, new ApiError(401, {}))).toBe(false)
  })

  it('retries server and network errors once', () => {
    expect(shouldRetry(0, new ApiError(503, {}))).toBe(true)
    expect(shouldRetry(1, new ApiError(503, {}))).toBe(false)
    expect(shouldRetry(0, new TypeError('Failed to fetch'))).toBe(true)
  })

  it('backs off up to 4 seconds', () => {
    expect([0, 1, 2, 3, 4].map(retryDelay)).toEqual([500, 1000, 2000, 4000, 4000])
  })
})
