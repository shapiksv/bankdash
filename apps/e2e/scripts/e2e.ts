/**
 * `npm run e2e [-- <playwright args>]`: starts the e2e stack unless it is already running, runs Playwright, and
 * stops the stack again only if this run started it. Exits with Playwright's exit code.
 */
import { spawn, type ChildProcess } from 'node:child_process'
import { resolve } from 'node:path'
import { abortOnSignals, down, isRunning, signalExitCode, up } from './stack.ts'

let playwright: ChildProcess | undefined
// Ctrl+C, a closed terminal or a cancelled CI job: Playwright gets a Ctrl+C (a second one makes it stop without
// waiting for running tests), and the stack is stopped once it has exited.
const abort = abortOnSignals(() => {
  if (playwright?.pid === undefined || playwright.exitCode !== null) return
  try {
    process.kill(-playwright.pid, 'SIGINT')
  } catch {
    // Already gone.
  }
})
const wasRunning = await isRunning()
let started = false
let code = 1
try {
  if (!wasRunning) {
    // On failure or a signal, up() has already stopped what it started.
    await up(abort)
    started = true
  }
  abort.throwIfAborted()
  code = await new Promise<number>((done) => {
    // Its own process group, so a terminal's Ctrl+C reaches it only once, through the handler above.
    playwright = spawn('npx', ['playwright', 'test', ...process.argv.slice(2)], {
      cwd: resolve(import.meta.dirname, '..'),
      detached: true,
      stdio: 'inherit',
    })
    playwright.on('exit', (exitCode) => done(exitCode ?? 1))
  })
} catch (error) {
  if (!abort.aborted) console.error(error instanceof Error ? error.message : error)
} finally {
  if (started) await down()
}
process.exit(signalExitCode(abort) ?? code)
