// @vitest-environment node
import { afterEach, describe, expect, it, vi } from 'vitest'

/** The dev server's /api proxy target with BANKDASH_API_URL set to `value` (undefined: not set). */
async function apiProxyTarget(value: string | undefined): Promise<unknown> {
  vi.stubEnv('BANKDASH_API_URL', value)
  vi.resetModules()
  const { default: config } = await import('./vite.config.ts')
  return config.server?.proxy?.['/api']
}

describe('the /api proxy', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('targets the dev gateway by default', async () => {
    expect(await apiProxyTarget(undefined)).toBe('http://localhost:8080')
  })

  it('targets BANKDASH_API_URL when it is set', async () => {
    expect(await apiProxyTarget('http://localhost:18080')).toBe('http://localhost:18080')
  })

  it('targets the dev gateway when BANKDASH_API_URL is empty', async () => {
    expect(await apiProxyTarget('')).toBe('http://localhost:8080')
  })
})
