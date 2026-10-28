/**
 * Starts and stops the e2e stack: Postgres and Kafka in their own compose project, auth, profile and account
 * services and the gateway from their boot jars (built once, run with `java -jar`, so each JVM is a direct child
 * in its own process group), and the Vite dev server, all on the ports of support/env.ts.
 *
 *   node scripts/stack.ts up     start what is not running yet and wait until everything is healthy
 *   node scripts/stack.ts down   stop what `up` started
 *
 * Everything `up` starts is recorded in .stack/state.json (PID plus start time, so a reused PID is never
 * mistaken for ours), and `down` stops only that. A port held by anything else aborts `up`: processes this
 * script did not start are never stopped. Logs go to .stack/logs/.
 *
 * One run at a time: `up`, `down` and all of `npm run e2e` hold .stack/lock (see {@link withStackLock}).
 */
import { execFileSync, spawn } from 'node:child_process'
import { existsSync, mkdirSync, openSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { setImmediate, setTimeout } from 'node:timers/promises'
import { fileURLToPath } from 'node:url'
import { COMPOSE_PROJECT, PORTS, WEB_URL } from '../support/env.ts'

const E2E_DIR = resolve(import.meta.dirname, '..')
const ROOT = resolve(E2E_DIR, '../..')
const WEB_DIR = join(ROOT, 'apps/web')
const STACK_DIR = join(E2E_DIR, '.stack')
const LOG_DIR = join(STACK_DIR, 'logs')
const STATE_FILE = join(STACK_DIR, 'state.json')
const LOCK_FILE = join(STACK_DIR, 'lock')

const HEALTH_TIMEOUT_MS = 180_000
const STOP_TIMEOUT_MS = 20_000

/** The signals that end a run (Ctrl+C, a closed terminal, a cancelled CI job), with exit code 128 + number. */
const STOP_SIGNALS = { SIGINT: 130, SIGHUP: 129, SIGTERM: 143 } as const
export type StopSignal = keyof typeof STOP_SIGNALS

/** A process this script started, identified by PID and start time. */
interface Proc {
  pid: number
  started: string
}

interface Started extends Proc {
  name: string
  port: number
  healthUrl: string
}

interface State {
  /** True once `up` has started the compose project (or taken over a leftover one): `down` then removes it. */
  compose: boolean
  processes: Started[]
}

const composeEnv = {
  ...process.env,
  COMPOSE_PROJECT_NAME: COMPOSE_PROJECT,
  POSTGRES_PORT: String(PORTS.postgres),
  KAFKA_EXTERNAL_PORT: String(PORTS.kafka),
}

const db = (name: string) => `--spring.datasource.url=jdbc:postgresql://localhost:${PORTS.postgres}/${name}`
const kafka = `--spring.kafka.bootstrap-servers=localhost:${PORTS.kafka}`
const authUrl = `--bankdash.services.auth=http://localhost:${PORTS.auth}`

const SERVICES = [
  { name: 'auth-service', port: PORTS.auth, args: [db('auth'), kafka] },
  { name: 'profile-service', port: PORTS.profile, args: [db('profile'), kafka, authUrl] },
  { name: 'account-service', port: PORTS.account, args: [db('account'), kafka, authUrl] },
  {
    name: 'api-gateway',
    port: PORTS.gateway,
    args: [
      authUrl,
      `--bankdash.services.profile=http://localhost:${PORTS.profile}`,
      `--bankdash.services.account=http://localhost:${PORTS.account}`,
      `--bankdash.cors.allowed-origins=${WEB_URL}`,
    ],
  },
]

/**
 * `abort` (see {@link abortOnSignals}) interrupts the start: `up` then stops what it started, once, and rejects
 * with the abort reason.
 */
export async function up(abort?: AbortSignal): Promise<void> {
  mkdirSync(LOG_DIR, { recursive: true })
  const previous = readState()
  if (previous) {
    if (await isHealthy(previous)) {
      log('The e2e stack is already running.')
      return
    }
    log('Stopping the rest of an earlier e2e stack that is no longer healthy.')
    await down()
  }

  // The compose project belongs to the e2e stack alone: one left over by an earlier run whose state is gone is
  // completed and then removed by `down` like a new one. Its running containers hold their own ports.
  const runningInfra = compose(['ps', '--status', 'running', '--services']).split('\n').filter(Boolean)
  const infraPorts = (['postgres', 'kafka'] as const).filter((s) => !runningInfra.includes(s)).map((s) => PORTS[s])
  const ports = [...SERVICES.map((s) => s.port), PORTS.web, ...infraPorts]
  const taken = ports.flatMap((port) => listeners(port).map((pid) => `  port ${port}: PID ${pid} ${command(pid)}`))
  if (taken.length > 0) {
    throw new Error(`Ports of the e2e stack are in use by processes this script did not start:\n${taken.join('\n')}`)
  }

  const state: State = { compose: true, processes: [] }
  // The steps below block the event loop, so a signal is only seen at the next checkpoint.
  const checkpoint = async () => {
    await setImmediate()
    abort?.throwIfAborted()
  }
  try {
    writeState(state)
    if (runningInfra.length > 0) log(`Reusing the running containers of an earlier e2e stack: ${runningInfra.join(', ')}.`)
    log('Starting Postgres and Kafka…')
    // Starts only what is not running yet.
    compose(['up', '-d', '--wait', 'postgres', 'kafka'], 'inherit')
    await checkpoint()
    log('Creating Kafka topics…')
    execFileSync(join(ROOT, 'infra/kafka/create-topics.sh'), { env: composeEnv, stdio: ['ignore', 'ignore', 'inherit'] })
    await checkpoint()

    if (!existsSync(join(WEB_DIR, 'node_modules'))) throw new Error('Run `npm install` in apps/web first.')
    log('Building the services…')
    const java = buildServices()
    await checkpoint()

    for (const service of SERVICES) {
      // The JVM settings of bootRun (build-logic/bankdash.spring-service): the dev profile by default, and UTC.
      const javaArgs = [
        '-Dspring.profiles.default=dev',
        '-Duser.timezone=UTC',
        '-jar',
        join(ROOT, `services/${service.name}/build/libs/${service.name}.jar`),
        `--server.port=${service.port}`,
        ...service.args,
      ]
      state.processes.push(start(service.name, service.port, `http://localhost:${service.port}/actuator/health`, java, javaArgs, ROOT))
      writeState(state)
    }
    state.processes.push(
      start('web', PORTS.web, WEB_URL, 'npm', ['run', 'dev', '--', '--port', String(PORTS.web), '--strictPort'], WEB_DIR, {
        BANKDASH_API_URL: `http://localhost:${PORTS.gateway}`,
      }),
    )
    writeState(state)

    log('Waiting for every service to be healthy…')
    await Promise.all(state.processes.map((p) => waitHealthy(p, abort)))
    log(`The e2e stack is up: ${WEB_URL} (logs in ${LOG_DIR})`)
  } catch (error) {
    // A Ctrl+C also reaches a blocking child (Gradle, compose), which then fails first: let the signal arrive.
    await setImmediate()
    if (abort?.aborted) {
      log(`Interrupted (${String(abort.reason)}); stopping what was started.`)
    } else {
      log('Starting the e2e stack failed; stopping what was started.')
    }
    await down()
    throw abort?.aborted ? abort.reason : error
  }
}

export async function down(): Promise<void> {
  const state = readState()
  if (!state) {
    if (compose(['ps', '--all', '--quiet']).trim() === '') {
      log('Nothing to stop: no e2e stack was started by this script.')
    } else {
      log('Removing the containers and volumes left over by an earlier e2e stack…')
      compose(['down', '-v'], 'inherit')
    }
    return
  }
  for (const p of [...state.processes].reverse()) {
    if (isOurs(p)) await stop(p.pid)
  }
  if (state.compose) {
    log('Removing the e2e Postgres and Kafka containers and volumes…')
    compose(['down', '-v'], 'inherit')
  }
  rmSync(STATE_FILE, { force: true })
  log('The e2e stack is stopped.')
}

/**
 * Runs `fn` while holding .stack/lock, so two runs never start or stop the stack at the same time, and no run
 * stops it under another run's tests. Fails at once if a live run holds the lock; the lock of a run that is gone
 * is taken over.
 */
export async function withStackLock<T>(fn: () => Promise<T>): Promise<T> {
  mkdirSync(STACK_DIR, { recursive: true })
  const me: Proc = { pid: process.pid, started: startTime(process.pid) ?? '' }
  while (!tryCreate(LOCK_FILE, JSON.stringify(me))) {
    const holder = readLock()
    if (holder === 'unreadable' || (holder !== undefined && isOurs(holder))) {
      const who = holder === 'unreadable' ? 'just started' : `PID ${holder.pid}: ${command(holder.pid)}`
      throw new Error(`Another e2e run is using the stack (${who}). Wait until it has finished.`)
    }
    if (holder) log(`Taking over the lock of an e2e run that is gone (PID ${holder.pid}).`)
    rmSync(LOCK_FILE, { force: true })
  }
  try {
    return await fn()
  } finally {
    const holder = readLock()
    if (holder !== 'unreadable' && holder?.pid === me.pid) rmSync(LOCK_FILE, { force: true })
  }
}

/** True if this call created `file`; false if it exists. */
function tryCreate(file: string, content: string): boolean {
  try {
    writeFileSync(file, content, { flag: 'wx' })
    return true
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === 'EEXIST') return false
    throw error
  }
}

/** The lock's holder; "unreadable" while it is being written (or if a run died right then: see below). */
function readLock(): Proc | 'unreadable' | undefined {
  try {
    return JSON.parse(readFileSync(LOCK_FILE, 'utf8')) as Proc
  } catch {
    if (!existsSync(LOCK_FILE)) return undefined
    // Written by a run that died between creating and writing it, if it is older than a few seconds.
    return Date.now() - statSync(LOCK_FILE).mtimeMs < 5000 ? 'unreadable' : undefined
  }
}

/** True if a stack started by this script is running and healthy. */
export async function isRunning(): Promise<boolean> {
  const state = readState()
  return state !== undefined && (await isHealthy(state))
}

function start(
  name: string,
  port: number,
  healthUrl: string,
  cmd: string,
  args: string[],
  cwd: string,
  env: Record<string, string> = {},
): Started {
  const out = openSync(join(LOG_DIR, `${name}.log`), 'w')
  // Its own process group, so `down` can signal it and everything it forked (npm → vite).
  const child = spawn(cmd, args, { cwd, env: { ...process.env, ...env }, detached: true, stdio: ['ignore', out, out] })
  child.unref()
  if (child.pid === undefined) throw new Error(`Could not start ${name}`)
  log(`Started ${name} (PID ${child.pid}) on port ${port}`)
  return { name, port, healthUrl, pid: child.pid, started: startTime(child.pid) ?? '' }
}

/** Stops the process group of a process started by {@link start}. */
async function stop(pid: number): Promise<void> {
  const signal = (sig: NodeJS.Signals) => {
    try {
      process.kill(-pid, sig)
    } catch {
      // Already gone.
    }
  }
  signal('SIGINT')
  const deadline = Date.now() + STOP_TIMEOUT_MS
  while (alive(pid) && Date.now() < deadline) await sleep(250)
  if (alive(pid)) signal('SIGKILL')
}

/**
 * Builds every service's boot jar in one Gradle run and returns the toolchain's java executable, the JDK that
 * bootRun would use.
 */
function buildServices(): string {
  const tasks = [...SERVICES.map((s) => `:services:${s.name}:bootJar`), `:services:${SERVICES[0]!.name}:printJavaLauncher`]
  const output = execFileSync(join(ROOT, 'gradlew'), ['-q', ...tasks], {
    cwd: ROOT,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'inherit'],
  })
  const java = output.trim().split('\n').at(-1) ?? ''
  if (!existsSync(java)) throw new Error(`Gradle did not report the toolchain's java executable:\n${output}`)
  return java
}

async function waitHealthy(p: Started, abort?: AbortSignal): Promise<void> {
  const deadline = Date.now() + HEALTH_TIMEOUT_MS
  while (Date.now() < deadline) {
    abort?.throwIfAborted()
    if (await healthy(p.healthUrl)) return
    if (!alive(p.pid)) throw new Error(`${p.name} exited during startup, see ${join(LOG_DIR, `${p.name}.log`)}`)
    await setTimeout(1000, undefined, { signal: abort }).catch(() => {})
  }
  throw new Error(`${p.name} was not healthy after ${HEALTH_TIMEOUT_MS / 1000} s, see ${join(LOG_DIR, `${p.name}.log`)}`)
}

async function isHealthy(state: State): Promise<boolean> {
  if (state.processes.length < SERVICES.length + 1 || !state.processes.every(isOurs)) return false
  const results = await Promise.all(state.processes.map((p) => healthy(p.healthUrl)))
  return results.every(Boolean)
}

async function healthy(url: string): Promise<boolean> {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(2000) })
    if (!response.ok) return false
    if (!url.includes('/actuator/health')) return true
    return ((await response.json()) as { status?: string }).status === 'UP'
  } catch {
    return false
  }
}

function isOurs(p: Proc): boolean {
  return alive(p.pid) && startTime(p.pid) === p.started
}

function alive(pid: number): boolean {
  try {
    process.kill(pid, 0)
    return true
  } catch {
    return false
  }
}

function startTime(pid: number): string | undefined {
  return run('ps', ['-o', 'lstart=', '-p', String(pid)]).trim() || undefined
}

function command(pid: number): string {
  return run('ps', ['-o', 'command=', '-p', String(pid)]).trim()
}

function listeners(port: number): number[] {
  return run('lsof', ['-nP', '-t', `-iTCP:${port}`, '-sTCP:LISTEN'])
    .split('\n')
    .filter(Boolean)
    .map(Number)
}

/** Output of a command, or "" if it fails (lsof and ps exit 1 when they find nothing). */
function run(cmd: string, args: string[]): string {
  try {
    return execFileSync(cmd, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] })
  } catch {
    return ''
  }
}

function compose(args: string[], stdio: 'pipe' | 'inherit' = 'pipe'): string {
  return (
    execFileSync('docker', ['compose', ...args], { cwd: ROOT, env: composeEnv, encoding: 'utf8', stdio: ['ignore', stdio, 'inherit'] }) ?? ''
  )
}

function readState(): State | undefined {
  return existsSync(STATE_FILE) ? (JSON.parse(readFileSync(STATE_FILE, 'utf8')) as State) : undefined
}

function writeState(state: State): void {
  writeFileSync(STATE_FILE, JSON.stringify(state, null, 2))
}

function sleep(ms: number): Promise<void> {
  return setTimeout(ms)
}

/**
 * Turns the signals that end a run into an abort instead of Node's immediate exit, so the work in progress stops
 * and the caller's cleanup runs exactly once. `onSignal` is called for every such signal.
 */
export function abortOnSignals(onSignal?: (signal: StopSignal) => void): AbortSignal {
  const controller = new AbortController()
  for (const signal of Object.keys(STOP_SIGNALS) as StopSignal[]) {
    process.on(signal, () => {
      controller.abort(signal)
      onSignal?.(signal)
    })
  }
  return controller.signal
}

/** The conventional exit code (128 + signal number) if a signal from {@link abortOnSignals} ended the run. */
export function signalExitCode(abort: AbortSignal): number | undefined {
  return abort.aborted ? STOP_SIGNALS[abort.reason as StopSignal] : undefined
}

function log(message: string): void {
  console.log(`[e2e stack] ${message}`)
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const action = process.argv[2]
  const actions: Record<string, (abort: AbortSignal) => Promise<void>> = { up, down }
  if (!actions[action ?? '']) {
    console.error('Usage: node scripts/stack.ts up|down')
    process.exit(2)
  }
  const abort = abortOnSignals()
  withStackLock(() => actions[action!]!(abort)).then(
    () => process.exit(signalExitCode(abort) ?? 0),
    (error: unknown) => {
      if (!abort.aborted) console.error(error instanceof Error ? error.message : error)
      process.exit(signalExitCode(abort) ?? 1)
    },
  )
}