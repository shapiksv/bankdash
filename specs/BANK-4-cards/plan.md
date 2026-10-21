# BANK-4: Cards and accounts for new customers — Plan

## Summary

Implements [spec.md](spec.md). A new `services/account-service` (port 8083, own `account` database) consumes
`auth.user-registered.v1`. For each new user it stores the customer's name, opens a CHECKING and a SAVINGS
account, and issues an INACTIVE debit card. Its card API (`/cards`, reached as `/api/cards` through the gateway)
lists cards, shows card details, adds a card to the caller's CHECKING or SAVINGS account, sets or changes the PIN
against the backend PIN policy, and blocks a card. Blocking emits `cards.status-changed.v1` through the
transactional outbox. The web app replaces the Credit Cards placeholder with the cards page, including a
confirmation step before blocking, and adds the Dashboard "My Cards" widget. account-service is modelled on
profile-service: JdbcClient repositories, Flyway, `libs/web-common` for security and errors, and `libs/messaging`
for the idempotent consumer, the outbox and the DLT.

## Design decisions

### D1. account-service is a copy of profile-service's shape
- **Decision:** same build (`bankdash.spring-service` + `libs:messaging` + `libs:web-common` + JDBC + Flyway),
  the same packages `com.bankdash.account.{api,app,domain,infra}`, JdbcClient repositories behind domain
  interfaces, a `Clock` bean, and `bankdash.messaging.outbox.enabled: true` because it produces events.
  The consumer group is `account-service` with `auto-offset-reset: earliest`, so users who registered before
  the service first started also get accounts.
- **Why:** the repo already has one working consumer service. Following its shape keeps the conventions in one
  place and lets the tests reuse the same base-class pattern (`AccountIT`).
- **Rejected:** Spring Data JDBC/JPA. The rest of the repo uses plain JdbcClient, and the card limit and
  block flows need explicit `FOR UPDATE` queries anyway.
- **ACs:** all backend ACs.

### D2. Registration creates customer, accounts and card in one transaction, guarded twice
- **Decision:** `UserRegisteredListener` (consumer name `account.user-registered`) reads `UserRegistered` with
  `EventPayloads.read` and runs `AccountService.openForNewCustomer` inside `IdempotentConsumer.process`. That
  method:
  1. runs `INSERT INTO customer … ON CONFLICT DO NOTHING`; if no row was inserted, the user is already set up,
     so it returns;
  2. otherwise inserts CHECKING and SAVINGS (balance 0.00, USD) and one DEBIT card linked to CHECKING, with
     status INACTIVE, no PIN and `holder_name = fullName`.
- **Why:** `processed_event` drops redeliveries of the same eventId (AC2). The customer insert, together with
  `unique (user_id, type)` on `account`, also covers a second event with a *different* eventId for the same
  user (for example a manual replay from the DLT), which `processed_event` cannot catch. One transaction means
  a failure rolls everything back, and the retry starts clean.
- **Rejected:** relying only on `processed_event`, which would let a replayed event create a second card.
  Splitting the work into several listeners, which would add ordering problems for no gain.
- **ACs:** AC1, AC2.

### D3. The holder name for added cards comes from a `customer` table (Q7)
- **Decision:** `customer (user_id PK, full_name, created_at)` is written from the event (D2).
  `POST /cards` reads `full_name` from it for the new card's `holder_name`.
- **Why:** this is the PO's answer to Q7. account-service gets one local copy of the name and never calls
  profile-service.
- **Note:** later name changes in profile-service do not reach account-service. That is out of scope (see
  Risks).
- **ACs:** AC6.

### D4. Card numbers are generated, and only last4 is kept
- **Decision:** `domain.CardNumbers.generate(RandomGenerator)` returns a 16-digit, Luhn-valid number starting
  with `4` from a `SecureRandom`. The service immediately keeps only `last4()`, so the full number is never
  stored, logged or returned. `CardNumbers.mask(last4)` returns `**** **** **** 1234`. The expiry is
  `YearMonth.now(clock).plusYears(3)`, stored as `expiry_month` / `expiry_year` and rendered as `MM/YY`.
- **Why:** this follows spec Scope §2 and §7 and architecture §6 (fake cards, last4 only). Luhn validity makes
  the fake numbers look realistic at no cost. The `Clock` makes expiry testable.
- **Rejected:** a unique full number, which would mean storing it, and the spec forbids that.
- **ACs:** AC1, AC3, AC4, AC6.

### D5. EXPIRED is derived on read; BLOCKED takes precedence
- **Decision:** the stored status is `INACTIVE | ACTIVE | BLOCKED`. `Card.displayStatus(YearMonth today)`
  returns `BLOCKED` if the card is blocked, otherwise `EXPIRED` if `today` is after the expiry month (the card is
  valid through the last day of that month, in UTC), and otherwise the stored status. `isExpired(today)` is used
  for the 409 on `PUT /pin` (AC12).
- **Why:** this is spec Scope §4. Showing a blocked card as BLOCKED, even after it expires, tells the user the
  more important fact, and the spec says blocking is final (Q1).
- **ACs:** AC4, AC12.

### D6. The PIN policy is a pure domain function with stable rule codes
- **Decision:** `domain.PinPolicy.check(String pin)` returns `Optional<PinRule>` and checks the rules of
  Scope §9 in the spec's order. The first rule that fails wins:

  | Order | `PinRule` (the ProblemDetail `rule`) | Detail message |
  |---|---|---|
  | 1 | `NOT_FOUR_DIGITS` | PIN must be exactly 4 digits |
  | 2 | `ALL_SAME_DIGITS` | PIN must not repeat one digit |
  | 3 | `CONSECUTIVE_DIGITS` | PIN must not be a sequence of consecutive digits |
  | 4 | `REPEATING_PAIR` | PIN must not be a repeating pair of digits |
  | 5 | `LOOKS_LIKE_YEAR` | PIN must not look like a year |
  | 6 | `KEYPAD_PATTERN` | PIN must not be a line on the keypad |
  | 7 | `SAME_AS_CURRENT` | New PIN must differ from the current PIN |

  The first six rules form the stateless check. `SAME_AS_CURRENT` is checked in the app layer with
  `PasswordEncoder.matches` against the stored hash. A violation throws `WeakPinException(rule)`. The handler
  maps it to **422**, `title: "Weak PIN"`, the detail message, and `rule`, matching the spec's example exactly.
- **Why:** AC10 lists `abcd` and `123` as weak PINs that get **422**, so the format check belongs to the policy
  and not to Bean Validation, which would answer 400. A missing or null `pin` is still a 400 validation error.
  A pure function makes it easy to test every rule with a parameterized test.
- **Rejected:** `@Pattern` on the request, which would give 400 for `abcd` and break AC10.
- **ACs:** AC6, AC8, AC9, AC10, AC11.

### D7. PINs are hashed with BCrypt and never echoed
- **Decision:** account-service defines a `PasswordEncoder` bean (`BCryptPasswordEncoder`, default strength).
  spring-security-crypto is already on the classpath through `libs/web-common`. The `Card` response has no PIN
  field, and PINs are never logged.
- **ACs:** AC8, AC9, AC11, and the security notes under Non-functional.

### D8. Ownership: every card query is scoped by user_id; foreign and missing cards return the same 404
- **Decision:** every repository lookup is `WHERE id = ? AND user_id = ?`. Both cases throw
  `CardNotFoundException`, which maps to 404 `title: "Card not found"` with a fixed detail, so the two bodies
  are byte-identical. The user always comes from `@CurrentUser`.
- **ACs:** AC3, AC5.

### D9. Order of checks for commands: 404, then 409, then 422
- **Decision:**
  - `PUT /cards/{id}/pin` locks the card with `SELECT … FOR UPDATE`, then checks not found (404), blocked or
    expired (409), the policy (422), and same as current (422).
  - `POST /cards` locks the customer row, then checks the account (404), the card limit (409) and the policy
    (422).
  - `POST /cards/{id}/block` locks the card, then checks not found (404) and already blocked (409).
- **Why:** the spec doesn't say which error wins when several apply. This order reports the resource first,
  then its state, then the input. It also means a blocked card never reveals anything about the PIN.
- **ACs:** AC5, AC10, AC12, AC14, AC16, AC19.

### D10. The card limit is serialized per user by locking the customer row
- **Decision:** `POST /cards` runs one transaction. It runs `SELECT … FROM customer WHERE user_id = ? FOR UPDATE`
  (404 if missing), finds the account of the requested type (404 if missing), and counts
  `status <> 'BLOCKED'` cards (409 if there are 5 or more). It then generates the card and inserts it as ACTIVE
  with the PIN hash. Expired cards count toward the limit, because the spec says "non-blocked".
- **Why:** without a lock, two concurrent requests both see 4 cards and create a 6th. The customer row is one
  row per user, so locking it serializes only that user's card creations. It doesn't touch the account rows,
  which transfers will lock in step 7.
- **Rejected:** `pg_advisory_xact_lock(hash)`, which is less obvious and can collide on the hash. Locking the
  account rows, which would contend with future transfers.
- **ACs:** AC16, AC19, AC6.

### D11. Blocking publishes `cards.status-changed.v1` through the outbox; only BLOCKED is published
- **Decision:** `CardService.block` locks the card, sets `BLOCKED`, updates `updated_at`, and calls
  `Outbox.publish(Topics.CARDS_STATUS_CHANGED, cardId, CardStatusChanged.TYPE, 1, new CardStatusChanged(cardId,
  userId, "BLOCKED"))` in the same transaction. Activating a card by setting its first PIN does **not** publish
  an event in this ticket.
- **Why:** AC13, the spec's Events table and its only planned consumer ("Card blocked") all describe blocking.
  Publishing activations too would be a contract nobody consumes yet. Adding them later is a compatible change
  (new status values, same topic).
- **Payload:** `record CardStatusChanged(@NotNull UUID cardId, @NotNull UUID userId, @NotBlank String status)`,
  `TYPE = "CardStatusChanged"`, `SCHEMA_VERSION = 1`, key = cardId (architecture §3). `status` is a String, so a
  future status value doesn't make old consumers fail to read the event.
- **ACs:** AC13, AC14.

### D12. Account selection by type (Q6)
- **Decision:** the `POST /cards` request is `{ accountType: "CHECKING" | "SAVINGS", pin }`. The service resolves the caller's
  account of that type. If the user has none yet, because the registration event hasn't been processed, the
  answer is 404 `title: "Account not found"`.
- **CR-1 (AC21):** an unknown or missing `accountType` is a 400 with detail "accountType must be CHECKING or
  SAVINGS". The request binds `accountType` as a nullable String, and the controller parses it into
  `AccountType`, so Jackson's enum failure ("Failed to read request") never answers. The 400 has the shared
  validation shape (`title: "Invalid request"`, `errors: [{ field: "accountType", … }]`) with that detail.
- **Rejected:** handling `HttpMessageNotReadableException` in the service. It would also catch malformed JSON on
  the other endpoints and give them the wrong detail.
- **ACs:** AC6, AC19, AC21.

### D13. `GET /cards` returns cards in a stable order with an optional limit
- **Decision (CR-1):** cards are sorted by `ORDER BY (status = 'BLOCKED'), created_at DESC, id DESC`: non-blocked
  cards first, newest first, then blocked cards, newest first (AC20). The order uses the stored status, as the card
  limit does, so an expired card counts as non-blocked. `limit` keeps the first N of that order. It is optional,
  `@Min(1) @Max(100)`, and anything outside that range is a 400. The response is `{ cards: [Card] }`.
- **Why:** the Dashboard widget asks for `limit=2` and gets the two newest non-blocked cards whenever the user has
  two. It drops any blocked card from its answer (D15), which only happens when fewer than two are non-blocked.
  No new index is needed: a user has a handful of cards, and `card_user_idx` already narrows to them.
- **Rejected:** sorting on the client. With `limit=2`, the server would already have cut the list in the wrong
  order. A `?status=` filter was also rejected: the page needs the blocked cards too, and one order serves both.
- **Superseded:** before CR-1 the order was `created_at, id` (oldest first).
- **ACs:** AC3, AC17, AC20.

### D14. Gateway routes
- **Decision:** add the route `account-service` with `uri: ${bankdash.services.account}` (default
  `http://localhost:8083`), `Path=/api/cards/**` and `StripPrefix=1`, as Scope §11 requires.
- **Rejected:** also routing `/api/accounts/**` now. No accounts endpoint exists in this ticket, so the route
  comes with the Accounts ticket (spec: Out of scope).
- **ACs:** AC18, plus every endpoint through the gateway.

### D15. Web: one cards module, dialogs for the destructive and form actions
- **Decision:**
  - `api/cards.ts` holds the types and fetch functions. `cards/queries.ts` holds the TanStack Query hooks, with
    keys that include the user id (the pattern of `profile/queries.ts`). `cards/pinRules.ts` mirrors D6 for
    instant feedback, but the backend's 422 `detail` is still shown when the backend rejects a PIN.
  - `CreditCardsPage` shows the card list, a details panel for the selected card, an "Add card" dialog
    (account select plus PIN), a "Set PIN" / "Change PIN" dialog (the label depends on the status), and a
    "Block card" button that opens a confirmation dialog. The request is sent only on "Confirm".
  - The Dashboard gets a `MyCardsWidget` that calls `GET /api/cards?limit=2` and leaves out blocked cards
    (CR-1). If the user has only blocked cards, it says so and links to the Credit Cards page.
  - `CreditCardsPage` keeps the API order (D13) and selects the first card by default.
  - An empty list shows an empty state ("Your cards are being prepared…") instead of an error.
- **Why:** this is spec Scope §5–8 and AC15/AC17. The query keys follow the existing rule that nothing cached
  for one user is shown to the next one.
- **Test plumbing:** `renderWithAuth` gets a default `GET /api/cards` → `{ cards: [] }`. Without it, every
  existing test that renders `/` (Dashboard) would fail with "Unexpected fetch".
- **ACs:** AC4, AC6, AC8, AC9, AC10, AC15, AC17.

## Modules and files

### libs/events
- `src/main/java/com/bankdash/events/CardStatusChanged.java`: new payload record.
- `src/main/java/com/bankdash/events/Topics.java`: adds `CARDS_STATUS_CHANGED = "cards.status-changed.v1"`.
- `src/test/java/com/bankdash/events/EventJsonTest.java`: pins the JSON shape.

### infra
- `infra/kafka/topics.txt`: adds `cards.status-changed.v1 3` and `cards.status-changed.v1.DLT 3`.

### services/account-service (new)
- `build.gradle.kts`
- `src/main/resources/application.yml`: port 8083, `account` datasource, Kafka group `account-service`, the outbox
  enabled, JWT settings as in profile-service.
- `src/main/resources/db/migration/V1__accounts_and_cards.sql`
- `src/main/java/com/bankdash/account/AccountServiceApplication.java`
- `domain/`: `AccountType`, `Account`, `Customer`, `Card`, `CardStatus`, `CardNumbers`, `PinPolicy`,
  `PinRule`, `WeakPinException`, `CardNotFoundException`, `AccountNotFoundException`,
  `CardLimitReachedException`, `CardAlreadyBlockedException`, `CardNotUsableException` (blocked or expired),
  `CustomerRepository`, `AccountRepository`, `CardRepository`.
- `app/`: `AccountService` (`openForNewCustomer`), `CardService` (`list`, `get`, `add`, `setPin`, `block`).
- `infra/`: `JdbcCustomerRepository`, `JdbcAccountRepository`, `JdbcCardRepository`, `UserRegisteredListener`,
  `ClockConfig`, `PinHashingConfig` (`PasswordEncoder`).
- `api/`: `CardController` (with `CardResponse`, `CardListResponse`, `AddCardRequest`, `SetPinRequest`),
  `CardExceptionHandler`.
- Tests: `AccountIT` (base), `AccountServiceApplicationIT`, `infra/UserRegisteredListenerIT`,
  `api/CardReadApiIT`, `api/CardPinApiIT`, `api/CardBlockApiIT`, `api/AddCardApiIT`, `domain/PinPolicyTest`,
  `domain/CardNumbersTest`, `domain/CardTest`.

### services/api-gateway
- `src/main/resources/application.yml`: `bankdash.services.account` and the `account-service` route.
- `src/test/java/com/bankdash/gateway/GatewayIT.java`: an account-service stub and the new routing and 401 tests.

### apps/web
- `src/api/cards.ts`, `src/api/cards.test.ts`
- `src/cards/queries.ts`, `src/cards/pinRules.ts`, `src/cards/pinRules.test.ts`
- `src/cards/CardTile.tsx` (masked card visual), `src/cards/ConfirmDialog.tsx`, `src/cards/PinDialog.tsx`,
  `src/cards/AddCardDialog.tsx`, `src/cards/MyCardsWidget.tsx`
- `src/pages/CreditCardsPage.tsx` (replaces the placeholder), `src/pages/CreditCardsPage.test.tsx`
- `src/pages/DashboardPage.tsx` (adds the widget), `src/pages/DashboardPage.test.tsx`
- `src/test/renderWithAuth.tsx` (default cards route), `src/test/users.ts` (card fixtures)

### Docs
- `CLAUDE.md`: the bootRun line for account-service and the `services/account-service` entry in the repository
  structure.

## Endpoints

All endpoints require a valid access token, and the user comes from `sub`. Gateway paths are shown; account-service
serves them without the `/api` prefix.

| Method | Gateway path | Request | Response | Errors (ProblemDetail) |
|---|---|---|---|---|
| GET | `/api/cards` | `?limit=` optional, 1–100 | 200 `{ cards: [Card] }`, ordered as in D13 (AC20) | 400 (bad limit), 401 |
| GET | `/api/cards/{id}` | — | 200 `Card` | 401, 404 `Card not found` |
| POST | `/api/cards` | `{ accountType, pin }` | 201 `Card` + `Location: /cards/{id}` | 400 (missing/unknown accountType → detail "accountType must be CHECKING or SAVINGS"; missing pin), 401, 404 `Account not found`, 409 `Card limit reached`, 422 `Weak PIN` + `rule` |
| PUT | `/api/cards/{id}/pin` | `{ pin }` | 204 | 400 (missing pin), 401, 404 `Card not found`, 409 `Card not usable` (blocked/expired), 422 `Weak PIN` + `rule` (incl. `SAME_AS_CURRENT`) |
| POST | `/api/cards/{id}/block` | — | 204 | 401, 404 `Card not found`, 409 `Card already blocked` |

`Card` = `{ id, accountId, type: "DEBIT", maskedNumber: "**** **** **** 1234", holderName, expiry: "MM/YY",
status: "INACTIVE" | "ACTIVE" | "BLOCKED" | "EXPIRED" }`, exactly the spec's example.

A non-UUID `{id}` is a 400 type mismatch from web-common. It is not a separate case in the spec, and it reveals
nothing about other users' cards.

**Gateway route change:** add a new route `account-service` → `${bankdash.services.account}`,
`Path=/api/cards/**`, `StripPrefix=1`. The JWT check is unchanged, so `/api/cards/**` without a
token gets 401 at the gateway (AC18).

## Events

| Topic | Partitions | Producer | Consumer | Payload record | Key |
|---|---|---|---|---|---|
| `auth.user-registered.v1` (exists) | 3 | auth-service (exists) | **account-service** `UserRegisteredListener`, consumer name `account.user-registered`, via `IdempotentConsumer.process` | `UserRegistered` (exists) | userId |
| `auth.user-registered.v1.DLT` (exists) | 3 | libs/messaging error handler | — (Kafka UI) | raw record | userId |
| `cards.status-changed.v1` (**new**) | 3 | **account-service** `CardService.block` via `Outbox.publish` | none yet (notification-service later) | `CardStatusChanged(cardId, userId, status)` (**new**) | cardId |
| `cards.status-changed.v1.DLT` (**new**) | 3 | libs/messaging error handler (once a consumer exists) | — | raw record | cardId |

The envelope comes from `EventEnvelope.of` in `Outbox.publish`, with `source = account-service`.
`infra/kafka/topics.txt` gets both new lines.

## Migrations

account-service: `src/main/resources/db/migration/V1__accounts_and_cards.sql`.

```sql
CREATE TABLE customer (
    user_id    uuid PRIMARY KEY,              -- auth's user id (JWT sub); no FK across services
    full_name  varchar(100) NOT NULL,
    created_at timestamptz  NOT NULL
);

CREATE TABLE account (
    id         uuid PRIMARY KEY,
    user_id    uuid          NOT NULL REFERENCES customer (user_id),
    type       varchar(16)   NOT NULL CHECK (type IN ('CHECKING', 'SAVINGS')),
    currency   varchar(3)    NOT NULL DEFAULT 'USD',
    balance    numeric(19,2) NOT NULL DEFAULT 0,
    created_at timestamptz   NOT NULL,
    CONSTRAINT account_user_type_uk UNIQUE (user_id, type)
);

CREATE TABLE card (
    id           uuid PRIMARY KEY,
    user_id      uuid        NOT NULL,
    account_id   uuid        NOT NULL REFERENCES account (id),
    type         varchar(16) NOT NULL CHECK (type IN ('DEBIT')),
    holder_name  varchar(100) NOT NULL,
    last4        char(4)     NOT NULL,
    expiry_month smallint    NOT NULL CHECK (expiry_month BETWEEN 1 AND 12),
    expiry_year  smallint    NOT NULL,
    pin_hash     varchar(100),               -- BCrypt; null until the first PIN is set
    status       varchar(16) NOT NULL CHECK (status IN ('INACTIVE', 'ACTIVE', 'BLOCKED')),
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    CHECK (status <> 'ACTIVE' OR pin_hash IS NOT NULL)   -- an INACTIVE card may be blocked without a PIN (AC13)
);
CREATE INDEX card_user_id_idx ON card (user_id, created_at, id);

-- outbox_event: the DDL of com.bankdash.messaging.Outbox, verbatim (plus its three indexes)
-- processed_event: the DDL of com.bankdash.messaging.IdempotentConsumer, verbatim
```

`card.user_id` duplicates `account.user_id`. This is deliberate: ownership checks and the card limit query use
one table. The spec's `card` row lists `user_id`.

## Test strategy

| Level | Class / file | ACs |
|---|---|---|
| Unit | `PinPolicyTest`: parameterized over every rule, the spec's examples (0000, 1234, 9876, 3210, 1212, 6969, 2020, 1998, 1950, 2029, 2580, 1593, abcd, 123, 12345) and accepted PINs (4826, 1949, 2030, 0192) | AC10 |
| Unit | `CardNumbersTest`: 16 digits, Luhn-valid, last4, mask format | AC3, AC1 |
| Unit | `CardTest`: displayStatus (EXPIRED derived, BLOCKED wins over EXPIRED), valid through the end of the expiry month | AC4, AC12 |
| Unit | `EventJsonTest.cardStatusChangedHasTheDocumentedShape` | AC13 |
| IT (Testcontainers) | `UserRegisteredListenerIT`: creation, duplicate event, same user with a new eventId, poison message → DLT, invalid payload → DLT | AC1, AC2 |
| IT | `CardReadApiIT`: list own only and masked, order (non-blocked newest first, then blocked) with and without limit, details, foreign = missing 404, no token 401 | AC3, AC4, AC5, AC18, AC20 |
| IT | `CardPinApiIT`: first PIN activates and is stored as a BCrypt hash, change PIN, weak PIN 422 with `rule` and PIN unchanged, same PIN 422, blocked/expired 409, foreign 404 | AC5, AC8–AC12 |
| IT | `CardBlockApiIT`: block → 204 + BLOCKED + event on `cards.status-changed.v1` (TopicReader, key = cardId), already blocked → 409 and no new outbox row, foreign 404 | AC5, AC13, AC14 |
| IT | `AddCardApiIT`: add to CHECKING and to SAVINGS (ACTIVE, holder name from customer), 6th non-blocked → 409 (blocked cards don't count), concurrent adds never exceed 5, no accounts yet → 404, weak PIN → 422, unknown or missing accountType → 400 with the CR-1 detail | AC6, AC10, AC16, AC19, AC21 |
| IT (no Docker) | `GatewayIT`: `/api/cards` and `/api/cards/{id}/block` reach account-service with the prefix stripped; `/api/cards` without a token or with a bad one → 401 and never reaches the stub | AC18 |
| Web (Vitest) | `pinRules.test.ts`: the same examples as `PinPolicyTest` | AC10 |
| Web | `api/cards.test.ts`: request shapes, including `accountType` | AC6 |
| Web | `CreditCardsPage.test.tsx`: lists all cards masked in the API order, empty state, details panel, block confirm / cancel, set first PIN, change PIN shows the backend `rule` detail, add card with an account type | AC4, AC6, AC8, AC9, AC10, AC15, AC17 |
| Web | `DashboardPage.test.tsx`: My Cards asks for `limit=2`, shows at most 2 masked cards and never a blocked one | AC17, AC20 |

Test names reference the ACs: `ac16_sixthNonBlockedCardIsRejected` and `it('AC15: Cancel sends no request')`.
Expired cards are created in the ITs by updating `expiry_year`/`expiry_month` directly with JdbcClient. The
service's `Clock` stays real.

## Smoke test

`/spec-implement` checks 8080/8081/8082/8083/5173 with `lsof` first and
uses alternate ports (for example 18080/18081/18082/18083, with `bankdash.services.*` pointing at them) for any
port that is taken. It never stops the user's processes.

```bash
docker compose up -d --wait && ./infra/kafka/create-topics.sh
./gradlew :services:auth-service:bootRun &      # 8081
./gradlew :services:account-service:bootRun &   # 8083
./gradlew :services:api-gateway:bootRun &       # 8080

GW=http://localhost:8080/api
TOKEN=$(curl -s -X POST $GW/auth/signup -H 'Content-Type: application/json' \
  -d '{"fullName":"Smoke Test","email":"smoke+'$RANDOM'@example.com","password":"correct-horse-1"}' | jq -r .accessToken)
H="Authorization: Bearer $TOKEN"

curl -s $GW/cards -H "$H" | jq                          # AC1/AC3: 1 card, INACTIVE, **** **** **** dddd (retry for a moment)
CARD=$(curl -s $GW/cards -H "$H" | jq -r '.cards[0].id')
curl -s $GW/cards/$CARD -H "$H" | jq                    # AC4
curl -si -X PUT $GW/cards/$CARD/pin -H "$H" -H 'Content-Type: application/json' -d '{"pin":"1234"}'  # AC10: 422 CONSECUTIVE_DIGITS
curl -si -X PUT $GW/cards/$CARD/pin -H "$H" -H 'Content-Type: application/json' -d '{"pin":"4826"}'  # AC8: 204, ACTIVE
curl -si -X POST $GW/cards -H "$H" -H 'Content-Type: application/json' -d '{"accountType":"SAVINGS","pin":"7391"}'  # AC6: 201
curl -si -X POST $GW/cards/$CARD/block -H "$H"          # AC13: 204; event visible in Kafka UI (cards.status-changed.v1)
curl -si -X POST $GW/cards/$CARD/block -H "$H"          # AC14: 409
curl -s "$GW/cards" -H "$H" | jq '.cards[].status'      # AC20: non-blocked newest first, BLOCKED last
curl -si -X POST $GW/cards -H "$H" -H 'Content-Type: application/json' -d '{"accountType":"CREDIT","pin":"7391"}'  # AC21: 400 detail
curl -si $GW/cards                                      # AC18: 401 problem+json
```

Then, in the web app (`npm run dev`): sign up, see the card on the Dashboard and the Credit Cards page, set a
PIN, add a card, and block one through the confirmation dialog (AC15, AC17).

## Risks / follow-ups

- **The holder name can go stale:** a name changed later in profile-service isn't propagated. A future
  `profile.updated` event could update `customer.full_name`.
- **No unblock** (Q1) and **no delete** (Q3), so blocked cards accumulate. They don't count toward the limit.
- **Activation isn't published:** only `BLOCKED` goes to `cards.status-changed.v1` (D11). Add `ACTIVE` when a
  consumer needs it.
- **`/api/accounts/**` is not routed yet:** the Accounts screen ticket adds the route with its endpoints
  (`GET /accounts/summary`).
- **No rate limit on PIN attempts:** already listed in architecture §7 and out of scope here.
- **BCrypt cost in tests:** the default strength (10) makes each PIN check about 50–100 ms, which is fine for
  this suite size. If the ITs become slow, make the strength configurable as auth-service does.
- **The date-of-birth PIN rule is deferred** (Q4). Revisit it if birth dates ever reach account-service.
- **Demo-mode seeding of history** (architecture §3, `demo` mode) belongs to the transaction-history ticket.
