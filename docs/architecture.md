# BankDash — Architecture

## Context

The repo contains only `docs/ui-analysis.md`, which lists 10 screens from the BankDash Figma mockup. We now need a backend architecture for a **learning project**: Java microservices behind an API Gateway, with JWT auth, PostgreSQL, Kafka and Docker Compose, plus a React + TypeScript frontend in the same monorepo.

- **MVP scope:** Dashboard, Transactions, Accounts, Credit Cards, Settings, Sign up / Login.
- **Phase 2:** Investments, Loans, Services.

**Decisions confirmed with the user:**
- Gradle Kotlin DSL.
- Our own auth-service that issues JWTs. No Keycloak.
- **Real** money movement: transfers change balances via a Kafka saga.
- About 5 business services plus the gateway.

**Versions** (checked on Maven Central, 2026-10-12):
- Java 25
- **Spring Boot 4.1.1**, the latest GA. 4.2.0 is still at milestone M2.
- **Spring Cloud 2025.1.3** (Oakwood), compatible with Boot 4.1.
- PostgreSQL 17, Kafka 4.x in KRaft mode (no ZooKeeper).
- **Node 24 LTS** for the frontend.

Implementation follows the steps in §5, one at a time.

---

## 1. Services, boundaries, entities

| Service | Responsibility | Owns (entities) |
|---|---|---|
| **api-gateway** (Spring Cloud Gateway, WebMVC) | Single entry point `/api/**`. Routes requests to services, validates JWTs (JWKS from auth-service), handles CORS and basic rate limiting on auth endpoints. No business logic. | — |
| **auth-service** | Sign up, login and refresh. Password change and the 2FA flag. Issues RS256 JWTs and publishes `/.well-known/jwks.json`. | `UserCredential` (id, email, passwordHash, twoFactorEnabled, status, createdAt), `RefreshToken` (id, userId, familyId, tokenHash, createdAt, expiresAt, familyExpiresAt, replacedBy, revoked; see `docs/adr/0001-jwt-and-refresh-tokens.md`) |
| **profile-service** | Personal data, preferences, Quick Transfer contacts, avatar. | `UserProfile` (userId, fullName, username, dateOfBirth, presentAddress, permanentAddress, city, postalCode, country, avatar), `Preferences` (userId, currency, timeZone, notifyOnTransfers, notifyOnMerchantOrders, notifyOnRecommendations), `Contact` (ownerId, contactUserId, displayName, position) |
| **account-service** | **Source of truth for money.** Accounts, balances and cards. Applies debits and credits atomically. Carries out the transfer step of the saga. Runs a demo-data simulator for card payments. | `Account` (id, userId, type CHECKING/SAVINGS, currency, balance), `Card` (id, userId, accountId, type DEBIT/CREDIT, bank, holderName, last4, expiry, pinHash, status ACTIVE/BLOCKED, primary), `Posting` (id, accountId, cardId?, direction, amount, balanceAfter, category, counterparty, transferId?, occurredAt; unique (transferId, accountId)) |
| **transaction-service** | Read-side history and analytics. Starts transfers and tracks their status. Generates receipts. Handles search. | `Transaction` (id, userId, accountId, cardId?, description, category, direction INCOME/EXPENSE, amount, balanceAfter, status, occurredAt, sourcePostingId unique), `Transfer` (id, fromUserId, toUserId, amount, note, idempotencyKey, requestHash, status PENDING/COMPLETED/FAILED, failureReason, createdAt; unique (fromUserId, idempotencyKey)) |
| **notification-service** | In-app notifications for the bell icon. Respects the user's notification preferences. | `Notification` (id, userId, type, title, body, read, createdAt), `NotificationSettings` (userId, flags — a local copy of Preferences) |

**Rules that apply to every service:**
- **Each service has its own database.** Services never read another service's tables.
- **Services are OAuth2 resource servers.** Each one validates the JWT against the same JWKS endpoint, and the user id comes from the token's `sub` claim.
- **Database migrations:** Flyway.
- **Errors:** Spring `ProblemDetail`, i.e. RFC 9457 error responses.
- **API docs:** each service exposes them through springdoc OpenAPI.

## 2. Screen → service mapping

Each frontend widget calls one endpoint, and the page fires these calls in parallel. There is no separate backend-for-frontend (BFF) layer.

| Screen / widget | Service · endpoint |
|---|---|
| **Header:** name and avatar | profile · `GET /profile/me` |
| **Header:** bell icon and unread count | notification · `GET /notifications`, `GET /notifications/unread-count` |
| **Header:** search | transaction · `GET /transactions?q=` (MVP searches transactions only) |
| **Sign up / Login** (we design these screens ourselves) | auth · `POST /auth/signup`, `/auth/login`, `/auth/refresh`, `/auth/logout`; `GET /auth/me` |
| **Dashboard:** My Cards | account · `GET /cards?limit=2` |
| **Dashboard:** Recent Transaction | transaction · `GET /transactions?size=3` |
| **Dashboard:** Weekly Activity | transaction · `GET /analytics/weekly-activity` |
| **Dashboard:** Expense Statistics | transaction · `GET /analytics/expenses-by-category` |
| **Dashboard:** Quick Transfer contacts | profile · `GET /contacts`, `POST /contacts` (add by username) |
| **Dashboard:** Quick Transfer → Send | transaction · `POST /transfers` with **required `Idempotency-Key` header** → `202` + `GET /transfers/{id}` (frontend polls) |
| **Dashboard:** Balance History | transaction · `GET /analytics/balance-history?months=7` |
| **Transactions:** cards, "+ Add Card" | account · `GET /cards` (the button navigates to the Credit Cards form) |
| **Transactions:** My Expense | transaction · `GET /analytics/monthly-expense?months=6` |
| **Transactions:** table, All/Income/Expense filter, pagination | transaction · `GET /transactions?direction=&page=&size=` |
| **Transactions:** Download receipt | transaction · `GET /transactions/{id}/receipt` (PDF) |
| **Accounts:** My Balance and Total Saving KPIs | account · `GET /accounts/summary` |
| **Accounts:** Income and Expense KPIs | transaction · `GET /analytics/summary?period=month` |
| **Accounts:** Last Transaction | transaction · `GET /transactions?size=3` |
| **Accounts:** My Card | account · `GET /cards?limit=1` |
| **Accounts:** Debit & Credit Overview | transaction · `GET /analytics/debit-credit-weekly` |
| **Accounts:** Invoices Sent | transaction · `GET /transfers?direction=outgoing&size=4` (we treat these as outgoing transfers) |
| **Credit Cards:** My Cards, Card List, View Details | account · `GET /cards`, `GET /cards/{id}` |
| **Credit Cards:** Card Expense Statistics | transaction · `GET /analytics/expenses-by-card` |
| **Credit Cards:** Add New Card | account · `POST /cards` |
| **Credit Cards:** Block Card / Change PIN | account · `POST /cards/{id}/block`, `PUT /cards/{id}/pin` |
| **Credit Cards:** Google Pay / Apple Pay | Stubs: buttons disabled, "coming soon" |
| **Settings:** Edit Profile | profile · `GET/PUT /profile/me`, `PUT /profile/me/avatar` |
| **Settings:** Preferences | profile · `GET/PUT /preferences` |
| **Settings:** Security | auth · `PUT /auth/password`, `PUT /auth/two-factor` (stores the flag only) |

## 3. Sync vs async

**Synchronous (REST):**
- **Browser → gateway → service** for all reads and commands in §2.
- **Service-to-service:** the only synchronous call is gateway/services fetching JWKS from auth-service, which is cached. No business service calls another business service synchronously. This is intentional: it keeps services independent.

**Asynchronous (Kafka).** Every event shares these rules:
- **Envelope:** `eventId`, `eventType`, `occurredAt`, `payload`.
- **Format:** JSON. Topics carry a `.v1` suffix.
- **Message key:** the one shown below, so events for the same key stay in order.
- **Sending:** through a **transactional outbox**.
- **Receiving:** consumers are **idempotent** (they keep a `processed_event` table).

| Topic | Producer | Key | Payload | Consumers → effect |
|---|---|---|---|---|
| `auth.user-registered.v1` | auth | userId | userId, email, fullName | **profile:** create profile and default preferences · **account:** open CHECKING + SAVINGS accounts and a default card (in `demo` mode, also seed history) · **notification:** welcome message |
| `profile.preferences-updated.v1` | profile | userId | userId, notification flags, currency, timeZone | **notification:** update its local `NotificationSettings` |
| `transfers.requested.v1` | transaction | fromUserId | transferId, fromUserId, toUserId, amount, note | **account:** debit sender and credit receiver's primary account in **one local DB transaction**. Fails on insufficient funds, a blocked card or an unknown receiver. |
| `transfers.completed.v1` | account | transferId | transferId, fromUserId, toUserId, amount | **transaction:** Transfer → COMPLETED · **notification:** notify both sides (if the preference allows) |
| `transfers.failed.v1` | account | transferId | transferId, reason | **transaction:** Transfer → FAILED + reason · **notification:** notify the sender |
| `accounts.posting-recorded.v1` | account | accountId | postingId, userId, accountId, cardId?, direction, amount, balanceAfter, category, counterparty, transferId?, occurredAt | **transaction:** insert a `Transaction` row. This feeds the history and every chart. |
| `cards.status-changed.v1` | account | cardId | cardId, userId, status | **notification:** "Card blocked" |

### Quick Transfer flow (choreography saga)

1. UI → `POST /transfers` with an `Idempotency-Key` header. See *Idempotency* below.
2. transaction-service saves `Transfer(PENDING)` and the outbox row in one transaction, then returns `202 {id, status}`.
3. Outbox → `transfers.requested.v1` → account-service applies it with **pessimistic locking**. See *Concurrency* below.
   - On success it writes two `Posting`s plus outbox rows: `posting-recorded` ×2 and `transfers.completed`.
   - On a business failure it writes `transfers.failed`. Insufficient funds is a normal outcome here, not an exception.
4. transaction-service updates the transfer status and stores both postings as transactions (one for the sender, one for the receiver).
5. The UI polls `GET /transfers/{id}` until the status is no longer PENDING, then refreshes the cards and transaction queries.

### Idempotency of `POST /transfers`

- **The header is required.** The UI generates a UUID when the form opens and reuses it for retries of the same submission. A missing header returns `400` ProblemDetail.
- **Uniqueness:** `unique (fromUserId, idempotencyKey)` on `Transfer`. Keys are scoped per user.
- **Repeated key with the same request:** returns the **same transfer**: `202` with the same `id` and its current status. No new event is emitted.
- **Repeated key with a different body:** `422` ProblemDetail ("Idempotency-Key reused with different request"). Detected via `requestHash`, a SHA-256 of the normalized body.
- **Race between two identical concurrent requests:** both try to insert. The second gets the unique-constraint violation, re-reads the row and returns it.
- **Second line of defense in account-service:** the `processed_event` table plus `unique (transferId, accountId)` on `Posting`. A redelivered `transfers.requested` can never move money twice.

### Concurrency in account-service

**Choice: pessimistic locking with a fixed lock order.**

**How it works**
- In one DB transaction, load both accounts with `SELECT … FROM account WHERE id IN (:a, :b) ORDER BY id FOR UPDATE`.
- Check the balance and card status.
- Update both balances, insert the postings and the outbox rows, then commit.

**Why this over optimistic locking + retry**
- **Contention is expected.** A popular receiver gets many concurrent credits. Under optimistic locking every collision means rollback and redo, and under load retries can be exhausted. That pushes a perfectly valid transfer to the DLT.
- **No deadlocks.** Locking in a fixed order (ascending account id) means two transfers A→B and B→A cannot deadlock.
- **Locks are held briefly.** Only for two row updates and a few inserts, in a single local DB. Waiting on a lock is cheaper than retrying.
- **Simpler code and tests.** There is no retry loop to reason about, and correctness is easy to show with an integration test: N parallel transfers, then assert the final balances.

**Also**
- **Ordering:** Kafka partitioning by `fromUserId` keeps one sender's transfers in order.
- **Lock timeout:** `lock_timeout` is set to 5 s. A timeout is treated as a retryable error (see below).

### Kafka consumer error handling

Every consumer uses Spring Kafka's `DefaultErrorHandler`, configured once in `libs/messaging`:

- **Retries:** `FixedBackOff(1s, 3)`, i.e. 3 retries for transient errors (DB unavailable, lock timeout).
- **Dead letter topic:** after retries are exhausted, `DeadLetterPublishingRecoverer` sends the record to **`<topic>.DLT`** (for example `transfers.requested.v1.DLT`). The original headers are kept and exception headers are added. DLT topics are created by `infra/kafka`.
- **Non-retryable exceptions go straight to the DLT:** deserialization errors (via `ErrorHandlingDeserializer`), validation errors and `IllegalArgumentException`.
- **Business failures are not exceptions.** Insufficient funds or a blocked card produce `transfers.failed`, never the DLT.
- **Inspecting the DLT:** with Kafka UI. Reprocessing is manual, which is a deliberate simplification. If a transfer's `requested` event lands in the DLT, the transfer stays PENDING and the UI shows "still processing".

## 4. Monorepo structure

```
bankdash/
├── CLAUDE.md                      # commands, structure, conventions (created in step 1)
├── settings.gradle.kts            # includes services/*, libs/*; includeBuild("build-logic")
├── gradle/libs.versions.toml      # version catalog: Boot 4.1.1, Cloud 2025.1.3, Testcontainers…
├── build-logic/                   # convention plugins: bankdash.java-library, bankdash.spring-service
├── libs/
│   ├── events/                    # event records + topic name constants (shared contract)
│   ├── messaging/                 # outbox + poller, idempotent-consumer helper, DefaultErrorHandler/DLT config
│   ├── web-common/                # ProblemDetail handler, CurrentUser resolver, security config
│   └── test-support/              # shared Testcontainers setup (Postgres + Kafka), JWT test helper
├── services/
│   ├── api-gateway/
│   ├── auth-service/
│   ├── profile-service/
│   ├── account-service/
│   ├── transaction-service/
│   └── notification-service/      # each: src/main/java/com/bankdash/<svc>/{api,domain,app,infra}
│                                  #       src/main/resources/db/migration (Flyway), src/test, Dockerfile
├── apps/
│   └── web/                       # React + TS + Vite (Node 24), React Router, TanStack Query,
│                                  # Recharts, Tailwind; Vitest + Testing Library; API types via openapi-typescript
├── infra/
│   ├── postgres/init.sql          # creates one database + user per service
│   └── kafka/                     # topic creation script (topics + .DLT topics)
├── docker-compose.yml             # infra only: postgres, kafka, kafka-ui
├── docker-compose.apps.yml        # all services + web (overlay for full-stack run, step 9)
└── docs/
    ├── ui-analysis.md
    ├── architecture.md            # this document
    └── adr/                       # short decision records (e.g. 0001-own-auth-service.md)
```

**Ports:**
- gateway: 8080
- auth: 8081
- profile: 8082
- account: 8083
- transaction: 8084
- notification: 8085
- web (Vite): 5173
- Kafka UI: 8090

## 5. Implementation order (something runs after every step)

**Testing applies to every step:**
- **Unit tests** cover domain and application logic: JUnit 5, AssertJ, Mockito.
- **Testcontainers integration tests** run against real Postgres and Kafka, via `@SpringBootTest` and `libs/test-support`. They cover the REST API, Flyway migrations, outbox publishing and Kafka consumers, including the DLT path where relevant.
- **Frontend:** Vitest + Testing Library for components with logic.
- **The ✅ check of every step includes "`./gradlew test` passes"**, plus `npm test` in `apps/web` once the web app has tests.

1. **Skeleton + CLAUDE.md**
   - **Build:** Gradle multi-project, convention plugins, version catalog, `libs/test-support`.
   - **Infra:** `docker-compose.yml` with Postgres, Kafka and Kafka UI.
   - **Web:** `apps/web` Vite app (Node 24) with the static layout: sidebar, header and empty pages for every route.
   - **`CLAUDE.md`:**
     - **Commands:** `./gradlew build`, `./gradlew test`, `./gradlew :services:<svc>:bootRun`, `docker compose up -d`, `cd apps/web && npm run dev / npm test`.
     - **Repo structure:** a short map of the folders above.
     - **Conventions:** English only in code, comments, docs and commits. Flyway for every schema change. ProblemDetail for errors. Domain events only via the transactional outbox. Every change ships with tests.
   - **Tests:** a smoke `contextLoads` test proving the Testcontainers setup works, and a layout render test in the web app.
   - ✅ `./gradlew test` passes, `docker compose up` is healthy, the UI shows navigation.
2. **Auth + Gateway + Login UI**
   - **auth-service:** signup, login and refresh, RS256 keys, JWKS.
   - **Gateway:** routes and JWT validation.
   - **Frontend:** Sign up and Login pages, token handling (access token in memory, refresh token in an httpOnly cookie), protected routes.
   - **Tests:**
     - Unit: password hashing, token issuing and expiry.
     - Integration: signup → login → refresh; duplicate email → 409; the gateway rejects a missing or invalid token with 401.
   - ✅ Sign up → log in → `/api/auth/me` through the gateway; `./gradlew test` passes.
3. **Profile + first Kafka event**
   - **Messaging:** outbox, idempotent consumer and the `DefaultErrorHandler`/DLT config in `libs/messaging`; `auth.user-registered`.
   - **profile-service:** creates the profile.
   - **Settings:** Edit Profile, Preferences, Security (password change).
   - **Header:** shows the name and avatar.
   - **Tests:**
     - Integration: the outbox publishes after commit; the consumer creates the profile; a duplicate event is ignored; a poison message lands in `auth.user-registered.v1.DLT`.
     - Validation errors return ProblemDetail.
   - ✅ New user → profile appears via Kafka; Settings save works; `./gradlew test` passes.
4. **Accounts & Cards**
   - **account-service:** consumes `user-registered` and opens accounts and a card.
   - **Credit Cards screen:** list, details, add, block, change PIN.
   - **Dashboard:** My Cards.
   - **Tests:**
     - Unit: card number masking, PIN rules.
     - Integration: accounts are created from the event; a user cannot see another user's card (403/404); block → `cards.status-changed` is emitted.
   - ✅ New user sees their cards; Block card works; `./gradlew test` passes.
5. **Transaction history**
   - **Events:** `accounts.posting-recorded` → transaction-service.
   - **Demo data:** a `demo`-profile simulator seeds about 7 months of card payments for each new user.
   - **Transactions screen:** table, filters, pagination, receipt PDF.
   - **Dashboard:** Recent Transaction.
   - **Tests:** integration for posting → transaction row (idempotent on `sourcePostingId`), filters, pagination, and the receipt endpoint returning a PDF.
   - ✅ Transactions page fully works; `./gradlew test` passes.
6. **Analytics & charts**
   - **Backend:** analytics endpoints (SQL aggregations).
   - **Dashboard:** Weekly Activity, Expense Statistics, Balance History.
   - **Transactions:** My Expense.
   - **Accounts screen:** KPIs, Debit & Credit Overview.
   - **Credit Cards:** Card Expense Statistics.
   - **Tests:** integration tests with fixed seeded transactions and a fixed `Clock`, asserting exact aggregates (week boundaries, month boundaries, empty periods).
   - ✅ Dashboard, Accounts and Credit Cards are complete except Quick Transfer; `./gradlew test` passes.
7. **Quick Transfer saga**
   - **profile-service:** contacts.
   - **transaction-service:** `POST /transfers` with `Idempotency-Key` → requested → completed / failed; the UI polls.
   - **account-service:** ordered `FOR UPDATE` locking.
   - **Accounts:** Invoices Sent widget.
   - **Tests:**
     - Idempotency: the same key returns the same transfer; the same key with a different body → 422; a missing key → 400.
     - Insufficient funds → FAILED.
     - Concurrency: 50 parallel transfers into one receiver plus opposite-direction pairs (A→B / B→A). Final balances are exact and there are no deadlocks.
     - A redelivered `transfers.requested` does not double-post.
   - ✅ Transfer between two users changes both balances; insufficient funds shows an error; `./gradlew test` passes.
8. **Notifications**
   - **notification-service:** consumes the events and `preferences-updated`.
   - **UI:** bell icon with unread count and a mark-as-read action.
   - **Tests:** integration for event → notification, preference off → no notification, mark-as-read, and the DLT on a malformed event.
   - ✅ Transfer → bell badge for the receiver; turning notifications off in Preferences stops them; `./gradlew test` passes.
9. **Full-stack run**
   - **`docker-compose.apps.yml`:** all services and the web app in containers. Dockerfiles use a layered Spring Boot jar on a Java 25 JRE.
   - **README:** prerequisites, how to run, the demo flow.
   - ✅ `docker compose -f docker-compose.yml -f docker-compose.apps.yml up` starts everything and the demo flow works.

**Phase 2 (outline only):**
- `investment-service` (read-mostly, seeded data).
- `loan-service`: **Repay** emits `loans.repayment-requested.v1`, which account-service debits. This reuses the saga, locking and idempotency patterns.
- `catalog-service` for Bank Services. Static data, so it could also live in profile-service.

## 6. Deliberate simplifications (learning project)

**Infrastructure**
- **One Postgres container** with one database per service, not one container per service.
- **One Kafka broker** (KRaft, replication factor 1). No Schema Registry: JSON events with versioned topic names, and Java records shared via `libs/events`. Sharing records like this creates coupling, which we accept.
- **Outbox via a scheduled poller**, not Debezium/CDC. Delivery is at-least-once, so idempotent consumers deal with duplicates.
- **DLT reprocessing is manual** (Kafka UI). There is no automated replay tool or alerting.
- **No service discovery or config server.** Docker Compose DNS names, static gateway routes, `application.yml` plus environment variables.

**Money and cards**
- **Single currency (USD).** The Preferences currency only changes how amounts are displayed. `BigDecimal` with scale 2 everywhere.
- **Fake cards.** Card numbers are generated and only `last4` is stored. No PCI concerns. The PIN is stored as a BCrypt hash.
- **External money is simulated.** Merchant payments and deposits come from a `demo`-profile simulator inside account-service.

**Features**
- **2FA is a stored flag only.** No TOTP in the MVP.
- **Google Pay and Apple Pay are UI stubs.**
- **Analytics are SQL aggregations** on the `transaction` table. No separate analytics store or CQRS projection DB.
- **Header search covers transactions only.**
- **Avatar is stored in Postgres** (`bytea`, ≤ 1 MB), not object storage.
- **Receipt is a simple generated PDF.**
- **Sign up / Login screens** are designed by us in the mockup's style, since the Design page doesn't have them.
- **"My Privileges"** stays a disabled menu item.

**Operations**
- **Observability:** Actuator health and metrics only. Distributed tracing can be added later as a learning step.
- **Deployment:** no Kubernetes or CI/CD pipeline in the MVP.

## 7. Known tech debt

Things we know are missing and intend to fix, unlike the deliberate simplifications in §6.

| Item | Since | Risk | Plan |
|---|---|---|---|
| **No rate limiting on `/api/auth/*`** (signup, login, refresh, logout). §1 lists it as a gateway responsibility, but step 2 shipped without it. | Step 2 | Online password guessing and signup spam are limited only by BCrypt's cost. | Per-IP and per-email limits at the gateway (e.g. Bucket4j), with `429` as a ProblemDetail and a `Retry-After` header. |

## Verification

- Each step in §5 ends with its own ✅ check. Every check includes `./gradlew test` passing, which runs the unit and Testcontainers integration tests, plus a manual check through the UI and with `curl` through the gateway on `:8080`.
- End-to-end smoke test after step 7:
  1. Register users A and B.
  2. A adds B as a contact.
  3. A sends $100, then sends again with the same Idempotency-Key.
  4. Check that only one transfer exists, both balances changed once, both transaction lists show it, and B gets a notification (after step 8).
- Watch the topics, and any `.DLT` topics, in Kafka UI (`:8090`).
