# BankDash

Learning project: a banking dashboard with a Java 25 / Spring Boot 4.1 microservices backend and a React + TypeScript frontend in one monorepo. Read `docs/architecture.md` before larger changes: it defines the services, events and the step-by-step implementation plan (§5). Implement one step at a time.

## Commands

Prerequisites: JDK 25 (Gradle toolchain; foojay downloads it if missing), Node 24, Docker.

```bash
# Backend (from the repo root)
./gradlew build                          # compile + all tests
./gradlew test                           # unit + Testcontainers integration tests (needs Docker)
./gradlew :services:<svc>:bootRun        # run one service, e.g. :services:auth-service:bootRun

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

## Local infrastructure

| What | From the host (bootRun, CLI) | From a container (docker-compose.apps.yml) |
|---|---|---|
| Kafka | `localhost:9092` (listener `EXTERNAL`) | `kafka:29092` (listener `INTERNAL`) |
| Postgres | `localhost:5432` | `postgres:5432` |
| Kafka UI | http://localhost:8090 | — |

- Kafka runs a single KRaft broker. **Auto topic creation is off**: topics and their `.DLT` topics must be created by `infra/kafka`.
- Postgres has one database and one user per service (`auth`, `profile`, `account`, `transaction`, `notification`). User = password = database name, and a service user can only connect to its own database. See `infra/postgres/init.sql`, which only runs when the volume is first created.
- Ports: gateway 8080, auth 8081, profile 8082, account 8083, transaction 8084, notification 8085, web 5173, Kafka UI 8090.

## Repository structure

```
build-logic/        Gradle convention plugins
                    bankdash.java-library   Java 25 toolchain, Spring BOMs, JUnit 5
                    bankdash.spring-service  Boot app with Actuator + web (for services/*)
gradle/libs.versions.toml   version catalog — all versions live here
libs/               shared Java libraries (events, messaging, web-common arrive in later steps)
  test-support/     Testcontainers Postgres + Kafka, published as test fixtures
services/           one Gradle project per microservice (from step 2)
apps/web/           React + TS + Vite frontend (layout/, pages/, routes.tsx, navigation.ts)
infra/              postgres/init.sql; kafka/ topic script (from step 3)
docs/               ui-analysis.md, architecture.md, adr/
docker-compose.yml  infra only: postgres, kafka, kafka-ui
```

Any folder under `libs/` or `services/` with a `build.gradle.kts` is included automatically (see `settings.gradle.kts`); no need to edit the settings file.

## Conventions

- **English only** in code, comments, docs and commit messages.
- **Every change ships with tests.** `./gradlew test` (and `npm test` for web changes) must pass before committing.
- **Flyway for every schema change**, in `src/main/resources/db/migration`. Never edit an applied migration; add a new one.
- **Errors are `ProblemDetail`** (RFC 9457).
- **Domain events only via the transactional outbox.** Never call `KafkaTemplate.send` from business code. Consumers are idempotent (`processed_event` table).
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
  Testcontainers must stay off runtime classpaths, so keep it in test fixtures only.
- Java packages: `com.bankdash.<svc>.{api,domain,app,infra}`.
- Small, focused commits; one branch and PR per implementation step.
