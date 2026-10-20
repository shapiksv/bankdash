import { QueryClient, type DefaultOptions } from '@tanstack/react-query'
import { ApiError } from './client'

/**
 * Retries a 404 a few times, because a new user's profile is created asynchronously from the
 * user-registered event and may not exist yet right after signup. Other client errors are final; network and
 * server errors get one more try.
 */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError) {
    if (error.status === 404) return failureCount < 5
    if (error.status < 500) return false
  }
  return failureCount < 1
}

/** 0.5 s, 1 s, 2 s, then 4 s at most. */
export function retryDelay(attempt: number): number {
  return Math.min(500 * 2 ** attempt, 4000)
}

export function createQueryClient(queries: DefaultOptions['queries'] = {}): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: shouldRetry, retryDelay, refetchOnWindowFocus: false, ...queries },
    },
  })
}
