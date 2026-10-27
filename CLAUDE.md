# BankDash

Learning project: a banking dashboard with a Java 25 / Spring Boot 4.1 microservices backend and a React + TypeScript frontend in one monorepo. Read `docs/architecture.md` before larger changes: it defines the services, boundaries and events. New work is specified per ticket in `specs/` (see Spec-Driven Development below).

## Commands

Prerequisites: JDK 25 (Gradle toolchain; foojay downloads it if missing), Node 24, Docker.

```bash
# Backend (from the repo root)
./gradlew build                          # compile + all tests
./gradlew test                           # unit + Testcontainers integration tests (needs Docker)
./gradlew :services:<svc>:bootRun        # run one service, e.g. :services:auth-service:bootRun (uses the dev profile)

# Local stack: infra + topics, then auth-service (8081), profile-service (8082), account-service (8083) and the gateway (8080), then the web app
./infra/kafka/create-topics.sh           # creates every topic of infra/kafka/topics.txt (safe to re-run)
./gradlew :services:auth-service:bootRun
./gradlew :services:profile-service:bootRun
./gradlew :services:account-service:bootRun
./gradlew :services:api-gateway:bootRun

# Infrastructure: Postgres, Kafka, Kafka UI (no .env needed; see .env.example for overrides)
docker compose up -d --wait
docker compose ps
docker compose down                      # add -v to wipe the database and Kafka volumes

# Frontend
cd apps/web && npm install
cd apps/web && npm run dev               # http://localhost:5173
cd apps/web && npm test                  # Vitest, single run (npm run test:watch to watch)
cd apps/web && npm run build && npm run lint
```

## Spec-Driven Development

The spec is the source of truth; code and tests must trace back to it. Each ticket gets a folder `specs/<TICKET-ID>-<short-name>/` (e.g. `specs/BANK-4-cards/`) holding `spec.md`, copied from `specs/_template/spec.md`, with numbered acceptance criteria AC1..ACn in Given/When/Then form. Tickets replace the §5 steps of `docs/architecture.md`, which stays the high-level architecture.

Workflow (each skill is user-invoked and takes the spec folder, e.g. `/spec-review specs/BANK-4-cards`):

1. **Write** `spec.md` from the template.
2. **`/spec-review`**: read-only; reports ambiguities, contradictions, missing or untestable ACs and conflicts with the architecture as numbered questions. Answer them in the spec.
3. **`/spec-plan`**: writes `plan.md` (design decisions with reasoning, files, endpoints, events, migrations) and `tasks.md` (small ordered tasks, each listing the ACs it covers), then stops until the plan is approved.
4. **`/spec-implement`**: branch named after the folder, one small commit per task, test names reference AC ids (`ac3_rejectsForeignAccount`, `it('AC3: ...')`), then all checks and a smoke test through the gateway. If the spec is wrong or incomplete it stops and asks; the spec is updated first, never silently bypassed.
5. **`/spec-verify`**: traceability table AC → test(s) → status → evidence, flags untested ACs, pushes and opens a draft PR with the table.

## Local infrastructure

| What | From the host (bootRun, CLI) | From a container (docker-compose.apps.yml) |
|---|---|---|
| Kafka | `localhost:9092` (listener `EXTERNAL`) | `kafka:29092` (listener `INTERNAL`) |
| Postgres | `localhost:5432` | `postgres:5432` |
| Kafka UI | http://localhost:8090 | — |

- Kafka runs a single KRaft broker. **Auto topic creation is off**: every topic and its `.DLT` must be listed in `infra/kafka/topics.txt`. `create-topics.sh` creates them locally, and `libs/test-support` creates them in the test broker (also with auto-create off), so a missing entry fails the integration tests.
- Postgres has one database and one user per service (`auth`, `profile`, `account`, `transaction`, `notification`). User = password = database name, and a service user can only connect to its own database. See `infra/postgres/init.sql`, which only runs when the volume is first created.
- Ports: gateway 8080, auth 8081, profile 8082, account 8083, transaction 8084, notification 8085, web 5173, Kafka UI 8090.

## Repository structure

```
build-logic/        Gradle convention plugins
                    bankdash.java-library   Java 25 toolchain, Spring BOMs, JUnit 5
                    bankdash.spring-service  Boot app with Actuator + web (for services/*)
gradle/libs.versions.toml   version catalog — all versions live here
libs/               shared Java libraries
  events/           event envelope, topic names, one record per payload (the contract between services)
  messaging/        auto-config: transactional Outbox + relay, IdempotentConsumer, DefaultErrorHandler → <topic>.DLT
  web-common/       auto-config for business services: JWT resource server, ProblemDetail errors, @CurrentUser
  test-support/     Testcontainers Postgres + Kafka (+ topics), TestJwtIssuer, TopicReader; published as test fixtures
services/           one Gradle project per microservice
  auth-service/     signup (emits auth.user-registered.v1), login, refresh, logout, /auth/me, password, two-factor, JWKS
  profile-service/  profile, preferences, avatar; creates profiles from auth.user-registered.v1
  account-service/  accounts and cards (balance summary, list, details, add, PIN, block → cards.status-changed.v1);
                    opens CHECKING, SAVINGS and a first card from auth.user-registered.v1
  api-gateway/      Spring Cloud Gateway (WebMVC): /api/** routes, JWT validation, CORS
apps/web/           React + TS + Vite frontend (api/, auth/, profile/, cards/, layout/, pages/, routes.tsx); TanStack Query;
                    Vite proxies /api to :8080
infra/              postgres/init.sql; kafka/topics.txt + create-topics.sh
specs/              one folder per ticket (spec.md, plan.md, tasks.md); _template/spec.md
docs/               ui-analysis.md, architecture.md, adr/ (0001: JWT and refresh tokens)
docker-compose.yml  infra only: postgres, kafka, kafka-ui
```

Any folder under `libs/` or `services/` with a `build.gradle.kts` is included automatically (see `settings.gradle.kts`); no need to edit the settings file.

## Conventions

- **English only** in code, comments, docs and commit messages.
- **Every change ships with tests.** `./gradlew test` (and `npm test` for web changes) must pass before committing.
- **Flyway for every schema change**, in `src/main/resources/db/migration`. Never edit an applied migration; add a new one.
- **Errors are `ProblemDetail`** (RFC 9457), including 401s from Spring Security.
- **Auth:** the browser holds the access token in memory only; the refresh token is the httpOnly `bd_refresh` cookie. Services validate JWTs against auth-service's JWKS (`iss=bankdash-auth`, `aud=bankdash-api`, user id = `sub`). CORS is configured only in the gateway. Details: `docs/adr/0001-jwt-and-refresh-tokens.md`.
- **Domain events only via the transactional outbox.** Never call `KafkaTemplate.send` from business code: call `Outbox.publish` inside the business transaction (producers set `bankdash.messaging.outbox.enabled: true` and own an `outbox_event` table). Listeners take a `ReceivedEvent`, read the payload with `EventPayloads.read` and wrap the handler in `IdempotentConsumer.process` (`processed_event` table). The DDL of both tables is in the Javadoc of `Outbox` and `IdempotentConsumer`; each service creates them in its own migration.
- **Kafka errors:** transient failures are retried 3 times (1 s apart), then the record goes to `<topic>.DLT`. Unreadable records and `InvalidEventException`, validation errors or `IllegalArgumentException` go there at once. Business outcomes (e.g. insufficient funds) are events, never exceptions.
- **Each service owns its database.** No cross-service table access and no synchronous calls between business services.
- **Versions** go in `gradle/libs.versions.toml`; Spring-managed libraries omit the version and use the Boot BOM.
- **Integration tests** use the shared containers, never local infra:
  ```kotlin
  testImplementation(testFixtures(project(":libs:test-support")))
  ```
  ```java
  @SpringBootTest
  @Import(ContainersConfig.class)
  class SomeIT { ... }
  ```
  Testcontainers must stay off runtime classpaths, so keep it in test fixtures only. A container starts only if the service has its client (JDBC / Kafka) on the classpath.
- Services that only validate tokens test against `TestJwtIssuer` (see `GatewayIT`, `ProfileIT`) instead of running auth-service. Business services get their security setup from `libs/web-common`.
- Tests and `bootRun` run in UTC (Postgres rejects some deprecated host zones such as `Europe/Kiev`).
- Java packages: `com.bankdash.<svc>.{api,domain,app,infra}`.
- Small, focused commits; one branch and PR per ticket (spec folder).
