import { GATEWAY_URL, WEB_URL } from './env.ts'

/** Fails fast with a hint instead of every test timing out when the stack is not running. */
export default async function globalSetup(): Promise<void> {
  for (const url of [`${GATEWAY_URL}/actuator/health`, WEB_URL]) {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(3000) })
      if (response.ok) continue
    } catch {
      // Reported below.
    }
    throw new Error(`${url} is not reachable. Start the stack with \`npm run stack:up\`, or run \`npm run e2e\`.`)
  }
}