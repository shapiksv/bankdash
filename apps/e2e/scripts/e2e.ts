/**
 * `npm run e2e [-- <playwright args>]`: starts the e2e stack unless it is already running, runs Playwright, and
 * stops the stack again only if this run started it. Exits with Playwright's exit code.
 */
import { spawn } from 'node:child_process'
import { resolve } from 'node:path'
import { down, isRunning, up } from './stack.ts'

const wasRunning = await isRunning()
let code = 1
try {
  if (!wasRunning) await up()
  // Ctrl+C reaches Playwright too (same process group); wait for it to finish, then clean up.
  process.on('SIGINT', () => {})
  code = await new Promise<number>((done) => {
    const child = spawn('npx', ['playwright', 'test', ...process.argv.slice(2)], {
      cwd: resolve(import.meta.dirname, '..'),
      stdio: 'inherit',
    })
    child.on('exit', (exitCode) => done(exitCode ?? 1))
  })
} catch (error) {
  console.error(error instanceof Error ? error.message : error)
} finally {
  if (!wasRunning) await down()
}
process.exit(code)