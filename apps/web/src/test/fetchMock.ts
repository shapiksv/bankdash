import { vi } from 'vitest'

type Handler = (request: Request) => Response | Promise<Response>

/** Replaces global fetch with a router keyed by "METHOD /path". Unrouted calls fail the test loudly. */
export function mockFetch(routes: Record<string, Handler>) {
  const calls: Request[] = []
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const request = new Request(new URL(String(input), 'http://localhost'), init)
    calls.push(request)
    const key = `${request.method} ${new URL(request.url).pathname}`
    const handler = routes[key]
    if (!handler) throw new Error(`Unexpected fetch: ${key}`)
    return handler(request)
  })
  vi.stubGlobal('fetch', fetchMock)
  return { calls, fetchMock }
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

export function problem(status: number, body: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ status, ...body }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}
