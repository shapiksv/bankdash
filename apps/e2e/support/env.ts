/**
 * The e2e stack's ports, shared by scripts/stack.ts and the tests. They are offset by 10000 from the dev ports
 * (CLAUDE.md), so the e2e stack runs next to a dev stack without touching it.
 */
export const PORTS = {
  postgres: 15432,
  kafka: 19092,
  gateway: 18080,
  auth: 18081,
  profile: 18082,
  account: 18083,
  web: 15173,
} as const

/** Separate from the dev project "bankdash": own containers and volumes. */
export const COMPOSE_PROJECT = 'bankdash-e2e'

export const WEB_URL = `http://localhost:${PORTS.web}`
export const GATEWAY_URL = `http://localhost:${PORTS.gateway}`

/** account-service's own database; user = password = database name (infra/postgres/init.sql). */
export const ACCOUNT_DB = {
  host: 'localhost',
  port: PORTS.postgres,
  database: 'account',
  user: 'account',
  password: 'account',
} as const