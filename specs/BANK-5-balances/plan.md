# BANK-5: Account balances on the Accounts page — Plan

## Summary

[spec.md](spec.md) adds a read-only `GET /api/accounts/summary` to account-service that returns the caller's
CHECKING balance as **My Balance** and SAVINGS balance as **Total Saving**, as exact 2-decimal JSON strings, with
`status: READY` only when both accounts exist and `PENDING` (amounts `null`) otherwise. The gateway routes
`/api/accounts/**` to account-service. The web Accounts page replaces its placeholder with the two KPI cards, which
show a loading placeholder, the USD-formatted balances, a "Setting up your accounts…" state that polls every 2 s for
at most 30 s, and an error state with Retry. No schema changes and no events.

## Design decisions

### D1. The summary is a pure domain value derived from the user's accounts
- **Decision:** a new record `domain/AccountSummary(Status status, @Nullable BigDecimal myBalance,
  @Nullable BigDecimal totalSaving)` with `static AccountSummary of(List<Account> accounts)`. It is `READY` with the
  CHECKING and SAVINGS balances when both types are present, otherwise `PENDING` with both amounts `null`. The
  currency is always `Account.CURRENCY` (USD).
- **Why:** Q1 (My Balance = CHECKING, Total Saving = SAVINGS) and Q7 (READY only when both exist) are pure rules
  and get fast unit tests, including the one-account case that "should not happen" (AC11) and is awkward to
  reach through the event flow.
- **Rejected:** computing it in SQL (`SUM(...) FILTER (WHERE type = ...)`): it hides the both-exist rule in a query
  and makes AC11 an IT-only concern.
- **ACs:** AC1, AC3, AC4, AC11.

### D2. One indexed read by user_id
- **Decision:** `AccountRepository.findByUser(UUID userId)` → `List<Account>`
  (`SELECT * FROM account WHERE user_id = ?`). `AccountService.summary(UUID userId)` calls it and returns
  `AccountSummary.of(...)`. No `@Transactional`: it is a single statement.
- **Why:** spec Non-functional "one indexed query by user_id"; the existing unique index `account_user_type_uk
  (user_id, type)` serves it through its leading column, so no migration is needed (Scope §6).
- **Rejected:** two `findByUserAndType` calls (two round trips, and the pair could be read across a concurrent
  insert).
- **ACs:** AC1, AC5.

### D3. Amounts are formatted to strings in the API layer, never serialized as BigDecimal
- **Decision:** `api/AccountController` returns `AccountSummaryResponse(String status, String currency,
  @Nullable String myBalance, @Nullable String totalSaving)`. Amounts go through
  `amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString()`; `UNNECESSARY` throws instead of rounding if a
  value ever had more than 2 decimals. `null` stays an explicit JSON `null` (Boot's default inclusion; the IT pins
  it with a strict body comparison).
- **Why:** Q3 / Scope §2 require JSON strings with exactly 2 decimals in plain notation (AC2). `toPlainString`
  never yields exponent form, and the string type makes the contract independent of Jackson settings.
- **Rejected:** `BigDecimal` fields with `@JsonFormat(shape = STRING)`: Jackson then uses `toString()`, whose
  notation depends on the scale and on `WRITE_BIGDECIMAL_AS_PLAIN`, i.e. on global config rather than the contract.
- **ACs:** AC1, AC2, AC3, AC4.

### D4. A new `AccountController` under `/accounts`
- **Decision:** `@RestController @RequestMapping("/accounts")` with `@GetMapping("/summary")`, the user from
  `@CurrentUser`. Security, the 401 ProblemDetail and the JWT checks come from `libs/web-common` unchanged.
  An unknown user (no rows) is a normal `PENDING`, never a 404 (Q2, Q10), so no exception handler is added.
- **ACs:** AC4, AC5, AC6.

### D5. Gateway: extend the existing account-service route
- **Decision:** the `account-service` route's predicate becomes `Path=/api/cards/**,/api/accounts/**` (the pattern
  of the profile-service route), `StripPrefix=1` unchanged.
- **Rejected:** a second route with the same `uri`: duplicated config for no behaviour difference.
- **ACs:** AC1 (through the gateway), AC6.

### D6. Web: amounts stay strings; a small string formatter
- **Decision:** `api/accounts.ts` types `myBalance` / `totalSaving` as `string | null`. `accounts/money.ts`
  exports `formatUsd(amount: string): string`: it checks `^\d+\.\d{2}$` (throws otherwise), inserts `,` every three
  digits of the integer part with a regex, and prefixes `$` — e.g. `"12345678901234.89"` → `$12,345,678,901,234.89`.
- **Why:** Scope §2 / §5 and Q5: fixed `en-US` grouping and `$`, and the value never becomes a JS `number` (AC8).
  Negative balances are out of scope (Q4), so the formatter rejects a sign rather than guessing its display.
- **Rejected:** `Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(string)`. It is exact on
  current engines (string input, NumberFormat v3), but the result depends on the runtime's ICU data and support, and
  it would accept malformed input silently. The hand-written version is 5 lines and fully deterministic.
- **ACs:** AC7, AC8.

### D7. Web: one query hook that owns polling and the 30 s cap
- **Decision:** `accounts/queries.ts` exports `useAccountSummary()`:
  - `useQuery` with key `['accounts', 'summary', userId]` (user id in the key, as for cards and profile) and
    `enabled: !!userId`.
  - `refetchInterval: (query) => query.state.data?.status === 'PENDING' && !timedOut ? 2000 : false` (Q6, Q10:
    query-specific, separate from the 404 retry of `queryClient.ts`, which never triggers here since the endpoint
    never returns 404).
  - A `pendingSince` ref is set on the first `PENDING` response. When a `PENDING` response arrives at or after
    `pendingSince + 30 s`, the hook marks itself timed out and polling stops.
  - It returns a view state `'loading' | 'pending' | 'ready' | 'error'` plus `summary` and `retry()`. `error` covers
    a failed query (any non-2xx or a network error, after the default single retry; Q9) and the timeout.
    `retry()` clears `pendingSince` and the timeout, then calls `refetch()`.
- **Why:** AC12 needs the elapsed time measured from the first pending answer, and the cards only need one
  four-way state. Measuring at response time needs no extra timer and works with Vitest fake timers.
- **Rejected:** a separate `setTimeout` for the cap (a second clock to keep in sync with the query) and doing the
  polling in `queryFn` (blocks the query in a loop and hides intermediate states).
- **ACs:** AC9, AC10, AC12.

### D8. Web: the Accounts page shows two KPI cards
- **Decision:** `accounts/KpiCard.tsx` (icon, label, body) and `accounts/BalanceKpis.tsx`, which renders My Balance
  and Total Saving from `useAccountSummary()`:
  - `loading`: a placeholder with `aria-busy="true"` and the accessible text "Loading…", distinct from the pending
    text (Q8).
  - `ready`: `formatUsd(...)` of each amount.
  - `pending`: "Setting up your accounts…" in both cards.
  - `error`: "Your balance could not be loaded." and a **Retry** button in both cards; either button calls `retry()`.
  `AccountsPage` replaces `EmptyPage` with a KPI row (a 4-column grid on desktop, of which this ticket fills two
  cells; Income and Expense are out of scope and are not rendered).
- **Test plumbing:** `renderWithAuth` gets a default `GET /api/accounts/summary` → READY with `"0.00"` for both
  (fixture `janeSummary` in `test/users.ts`), so `RequireAuth.test.tsx`, which renders `/accounts`, does not hit
  "Unexpected fetch".
- **ACs:** AC7, AC8, AC9, AC10.

## Modules and files

### services/account-service
- `domain/AccountSummary.java` (new), `domain/AccountRepository.java` (`findByUser`).
- `infra/JdbcAccountRepository.java` (`findByUser`).
- `app/AccountService.java` (`summary`).
- `api/AccountController.java` (new; `AccountSummaryResponse`).
- Tests: `domain/AccountSummaryTest.java` (new), `api/AccountSummaryApiIT.java` (new).

### services/api-gateway
- `src/main/resources/application.yml`: `/api/accounts/**` on the account-service route.
- `src/test/java/com/bankdash/gateway/GatewayIT.java`: routing and 401 tests for `/api/accounts/summary`.

### apps/web
- `src/api/accounts.ts` (new): `AccountSummary` type, `fetchAccountSummary()`.
- `src/accounts/money.ts`, `src/accounts/money.test.ts` (new).
- `src/accounts/queries.ts` (new): `useAccountSummary`.
- `src/accounts/KpiCard.tsx`, `src/accounts/BalanceKpis.tsx` (new).
- `src/pages/AccountsPage.tsx` (replaces the placeholder), `src/pages/AccountsPage.test.tsx` (new).
- `src/test/renderWithAuth.tsx` (default summary route), `src/test/users.ts` (`janeSummary`).

### libs/*, infra/*
- No changes: no events, no topics, no migrations.

### Docs
- `CLAUDE.md`: the account-service line in the repository structure mentions the balance summary.

## Endpoints

| Method | Gateway path | Auth | Request | Response | Errors (ProblemDetail) |
|---|---|---|---|---|---|
| GET | `/api/accounts/summary` | access token; user = `sub` | — | 200 `AccountSummary` | 401 (no/invalid token, from the gateway or web-common); 502/503 from the gateway when account-service is down |

account-service serves it as `GET /accounts/summary`. Response, exactly the spec's contract:

```json
{ "status": "READY",   "currency": "USD", "myBalance": "1250.50", "totalSaving": "300.00" }
{ "status": "PENDING", "currency": "USD", "myBalance": null,      "totalSaving": null }
```

**Gateway route change:** the `account-service` route matches `Path=/api/cards/**,/api/accounts/**`,
`StripPrefix=1`. The JWT check is unchanged, so `/api/accounts/**` without a valid token is a 401 at the gateway.

## Events

None (spec: Events). No new topics, no `infra/kafka/topics.txt` change, no outbox or consumer changes. The accounts
still come from the existing `auth.user-registered.v1` consumer (BANK-4).

## Migrations

None (Scope §6). The read uses `account (user_id, type, balance numeric(19,2))` and the existing unique index
`account_user_type_uk (user_id, type)` from `V1__accounts_and_cards.sql`.

## Test strategy

| Level | Class / file | ACs |
|---|---|---|
| Unit | `AccountSummaryTest`: both accounts → READY with CHECKING/SAVINGS balances (and not swapped); zeros; only CHECKING → PENDING; only SAVINGS → PENDING; none → PENDING | AC1, AC3, AC4, AC11 |
| IT (Testcontainers) | `AccountSummaryApiIT`: balances seeded with JdbcClient (Q12); READY 1250.50/300.00; 12345678901234.89/0.10 compared as raw JSON (`"myBalance":"12345678901234.89"`, a string node, no exponent); freshly registered → `"0.00"`; unknown user → PENDING with explicit `null`s; customer with only a CHECKING row (inserted via JDBC) → PENDING; two users see only their own; no token / expired token → 401 problem+json | AC1–AC6, AC11 |
| IT (no Docker) | `GatewayIT`: `/api/accounts/summary` reaches the account stub as `/accounts/summary` with the token; without a token / with an invalid one → 401 and never reaches the stub | AC1, AC6 |
| Web (Vitest) | `money.test.ts`: `1250.50` → `$1,250.50`, `300.00`, `0.00`, `999.99`, `1000.00`, `12345678901234.89` → `$12,345,678,901,234.89`; malformed input throws | AC7, AC8 |
| Web | `AccountsPage.test.tsx`: loading placeholder; READY values; the large value exactly; PENDING text in both cards; 500 and a network error → error + Retry, Retry requests again and shows the values; PENDING → PENDING → READY polls every 2 s and then stops; PENDING for 30 s → error and no more requests; Retry after the timeout restarts | AC7–AC10, AC12 |

Test names reference the ACs (`ac2_amountsAreExactJsonStrings`, `it('AC12: stops polling after 30 seconds')`).
Polling tests use `vi.useFakeTimers({ shouldAdvanceTime: true })` and count `GET /api/accounts/summary` calls.

## Smoke test

`/spec-implement` checks 8080/8081/8083/5173 with `lsof` first and uses alternate ports (for example
18080/18081/18083, with `bankdash.services.*` pointing at them) for any port that is taken; it never stops the
user's processes.

```bash
docker compose up -d --wait && ./infra/kafka/create-topics.sh
./gradlew :services:auth-service:bootRun &      # 8081
./gradlew :services:account-service:bootRun &   # 8083
./gradlew :services:api-gateway:bootRun &       # 8080

GW=http://localhost:8080/api
TOKEN=$(curl -s -X POST $GW/auth/signup -H 'Content-Type: application/json' \
  -d '{"fullName":"Smoke Test","email":"smoke+'$RANDOM'@example.com","password":"correct-horse-1"}' | jq -r .accessToken)
H="Authorization: Bearer $TOKEN"
USER_ID=$(curl -s $GW/auth/me -H "$H" | jq -r .id)

curl -s $GW/accounts/summary -H "$H" | jq      # AC4: PENDING with nulls, if it is fast enough to beat the consumer
sleep 3; curl -s $GW/accounts/summary -H "$H" | jq   # AC3: READY, "0.00" / "0.00"

docker compose exec -T postgres psql -U account -d account -c \
  "UPDATE account SET balance = CASE type WHEN 'CHECKING' THEN 1250.50 ELSE 300.00 END WHERE user_id = '$USER_ID'"
curl -s $GW/accounts/summary -H "$H"            # AC1: {"status":"READY","currency":"USD","myBalance":"1250.50","totalSaving":"300.00"}
curl -si $GW/accounts/summary                   # AC6: 401 application/problem+json
```

Then, in the web app (`npm run dev`): sign up, open Accounts, see "Setting up your accounts…" turn into `$0.00`
(AC9, AC12), and after the SQL update and a reload see `$1,250.50` and `$300.00` (AC7).

## Risks / follow-ups

- **Negative balances** (Q4): `formatUsd` rejects a sign; the transfers ticket must decide the display and extend it.
- **Display-currency preference** (Q5) is not applied; a later ticket.
- **Income / Expense KPIs** and the other Accounts widgets need transaction-service (out of scope).
- **Stuck PENDING** (event in the DLT) only surfaces as the UI error after 30 s; reprocessing stays manual
  (architecture §3, §6).
- **The 30 s cap is per page visit:** leaving and reopening the page starts a new 30 s window.
- **Cache invalidation:** nothing changes balances yet; the transfers ticket must invalidate
  `['accounts', 'summary', userId]` after a transfer completes.
