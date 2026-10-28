/**
 * `npm run e2e [-- <playwright args>]`: starts the e2e stack unless it is already running, runs Playwright, and
 * stops the stack again only if this run started it. Exits with Playwright's exit code.
 */
import { spawn } from 'node:child_process'
import { resolve } from 'node:path'
import { abortOnSignals, down, isRunning, signalExitCode, up } from './stack.ts'

const abort = abortOnSignals()
const wasRunning = await isRunning()
let started = false
let code = 1
try {
  if (!wasRunning) {
    // On failure or a signal, up() has already stopped what it started.
    await up(abort)
    started = true
  }
  // Ctrl+C reaches Playwright too (same process group); wait for it to finish, then clean up.
  code = await new Promise<number>((done) => {
    const child = spawn('npx', ['playwright', 'test', ...process.argv.slice(2)], {
      cwd: resolve(import.meta.dirname, '..'),
      stdio: 'inherit',
    })
    child.on('exit', (exitCode) => done(exitCode ?? 1))
  })
} catch (error) {
  if (!abort.aborted) console.error(error instanceof Error ? error.message : error)
} finally {
  if (started) await down()
}
process.exit(signalExitCode(abort) ?? code)
