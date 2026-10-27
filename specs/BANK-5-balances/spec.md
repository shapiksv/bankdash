# BANK-5: Account balances on the Accounts page

- **Status:** Reviewed
- **Services:** services/account-service, services/api-gateway, apps/web

## Context

Customers want to see how much money they have. The Accounts page (see `docs/ui-analysis.md`, Accounts)
shows four KPI cards at the top; this ticket delivers two of them: **My Balance** and **Total Saving**.

Builds on `docs/architecture.md`: account-service owns accounts and balances (§1). Accounts are created
asynchronously from `auth.user-registered.v1` (BANK-4), so for a few seconds after sign-up a customer may
have no accounts yet.

## Scope

1. New endpoint `GET /api/accounts/summary` in account-service returning:
  - **My Balance** = balance of the customer's CHECKING account (see Q1);
  - **Total Saving** = balance of the customer's SAVINGS account (see Q1);
  - the currency (USD).
2. Amounts are exact decimals, transported as JSON **strings** with exactly 2 decimal places (e.g. `"1250.50"`),
   never as JSON numbers (see Q3). The backend uses `BigDecimal`; the frontend never converts amounts
   to JavaScript `number`.
3. The summary is `READY` only when both the CHECKING and the SAVINGS account exist; otherwise the endpoint
   returns 200 with `"status": "PENDING"` instead of an error (see Q2, Q7). The UI shows a "Setting up your
   accounts…" state and polls every 2 seconds for at most 30 seconds, then shows an error with a Retry button
   (see Q6).
4. Gateway route `/api/accounts/**` → account-service.
5. Accounts page: the **My Balance** and **Total Saving** KPI cards, always formatted in USD with `$` and fixed
   `en-US` grouping, e.g. `$1,250.50` (see Q5).
6. No database schema changes: the existing `account` table already holds the balances.

## Out of scope

- The **Income** and **Expense** KPI cards (they need transaction history, a later ticket).
- Other Accounts page widgets: Last Transaction, My Card, Debit & Credit Overview, Invoices Sent.
- Multiple currencies (USD only, `docs/architecture.md` §6).
- Applying the display-currency preference (Settings → Preferences) to amounts: a later ticket (see Q5).
- Changing balances (deposits, transfers): later tickets.
- Negative balances: not possible yet, no operation can debit an account (see Q4).

## User stories

- As a customer, I want to see my current balance and my total savings at the top of the Accounts page,
  so that I know how much money I have at a glance.
- As a new customer, I want to see that my accounts are being set up instead of an error,
  so that I trust the app right after signing up.

## Acceptance criteria

- **AC1** — Summary for a customer with accounts
  - **Given** a customer whose CHECKING balance is 1250.50 and SAVINGS balance is 300.00
  - **When** they call `GET /api/accounts/summary` through the gateway
  - **Then** the response is 200 with `status: "READY"`, `currency: "USD"`, `myBalance: "1250.50"`, `totalSaving: "300.00"`
- **AC2** — Amounts are exact strings
  - **Given** a customer whose CHECKING balance is 12345678901234.89 and SAVINGS balance is 0.10
  - **When** they call `GET /api/accounts/summary`
  - **Then** `myBalance` is the JSON string `"12345678901234.89"` and `totalSaving` is the JSON string `"0.10"`
    (plain notation, never a JSON number or exponent form)
- **AC3** — Zero balances
  - **Given** a new customer whose accounts were just created (both balances 0)
  - **When** they call `GET /api/accounts/summary`
  - **Then** the response is 200 with `status: "READY"`, `myBalance: "0.00"`, `totalSaving: "0.00"`
- **AC4** — Accounts not created yet
  - **Given** a signed-up customer for whom account-service has not yet processed `auth.user-registered.v1`
  - **When** they call `GET /api/accounts/summary`
  - **Then** the response is 200 with `status: "PENDING"`, `currency: "USD"`, and `myBalance` and `totalSaving` are `null`
- **AC5** — Only the caller's accounts
  - **Given** two customers with different balances
  - **When** each calls `GET /api/accounts/summary`
  - **Then** each response contains only that customer's balances
- **AC6** — No token
  - **Given** no access token or an invalid one
  - **When** `GET /api/accounts/summary` is called through the gateway
  - **Then** the response is 401 ProblemDetail
- **AC7** — KPI cards in the UI
  - **Given** a logged-in customer with summary `myBalance: "1250.50"`, `totalSaving: "300.00"`
  - **When** they open the Accounts page
  - **Then** the My Balance card shows `$1,250.50` and the Total Saving card shows `$300.00`
- **AC8** — Exact formatting in the UI
  - **Given** a summary with `myBalance: "12345678901234.89"`
  - **When** the Accounts page renders it
  - **Then** the card shows `$12,345,678,901,234.89` exactly (no rounding, no floating-point artefacts)
- **AC9** — Pending state in the UI
  - **Given** the summary returns `status: "PENDING"`
  - **When** the customer opens the Accounts page
  - **Then** both cards show "Setting up your accounts…" (no error, no empty page)
- **AC10** — Error state in the UI
  - **Given** the summary request fails with a 5xx response or a network error
  - **When** the Accounts page renders
  - **Then** both cards show an error with a Retry button, and clicking Retry requests the summary again
- **AC11** — Only one account exists
  - **Given** a customer with a CHECKING account but no SAVINGS account
  - **When** they call `GET /api/accounts/summary`
  - **Then** the response is 200 with `status: "PENDING"`, `currency: "USD"`, and `myBalance` and `totalSaving` are `null`
- **AC12** — Polling while pending
  - **Given** the summary returns `status: "PENDING"`
  - **When** the Accounts page is open
  - **Then** the page requests the summary again every 2 seconds; as soon as a response has `status: "READY"` the
    cards show the balances and polling stops; if the status is still `PENDING` after 30 seconds, polling stops and
    the cards show the error state of AC10

## API contract

Paths as seen through the gateway (`/api/...`). The user is taken from the token (`sub`), never from the request.

| Method | Path | Auth | Request | Response | Errors (ProblemDetail) |
|---|---|---|---|---|---|
| GET | /api/accounts/summary | access token | — | 200 `AccountSummary` | 401; 502/503 (from the gateway) |

`AccountSummary`, ready:

```json
{
  "status": "READY",
  "currency": "USD",
  "myBalance": "1250.50",
  "totalSaving": "300.00"
}
```

`AccountSummary`, accounts not created yet:

```json
{
  "status": "PENDING",
  "currency": "USD",
  "myBalance": null,
  "totalSaving": null
}
```

## Events

None. The endpoint only reads data; accounts are created by the existing `auth.user-registered.v1` consumer (BANK-4).

## Data model

No changes. Reads the existing `account` table (account-service): `balance numeric(19,2)` per `(user_id, type)`.
My Balance and Total Saving are derived on read, never stored.

## Non-functional

- **Security:** a customer only ever sees their own balances; the user comes from the token.
- **Precision (Finance):** `BigDecimal` with scale 2 end to end; amounts travel as JSON strings; the frontend formats
  the decimal string without converting it to a JavaScript `number`.
- **Consistency:** accounts appear asynchronously after sign-up; `PENDING` covers that window.
- **Performance:** one indexed query by `user_id`.

## Edge cases & errors

| Case | Expected behaviour | HTTP status / event |
|---|---|---|
| Accounts not created yet | `status: PENDING`, amounts `null`, UI polls (AC4, AC9, AC12) | 200 |
| Still `PENDING` after 30 s of polling (e.g. the event went to the DLT) | Polling stops; error with a Retry button (AC12, AC10) | 200 |
| Both balances are 0 | `"0.00"` for both (AC3) | 200 |
| Very large balance | Returned and shown exactly (AC2, AC8) | 200 |
| No / invalid token | Error (AC6) | 401 |
| Only one of the two accounts exists (should not happen: both are created in one transaction) | Treated as `PENDING` (AC11) | 200 |
| Account-service unavailable | Gateway error; UI shows an error with a Retry button (AC10) | 502/503 |
| First request still loading | The cards show a loading placeholder, distinct from the pending text | — |

## Open questions

1. What exactly are "My Balance" and "Total Saving"?
   Options: (a) My Balance = CHECKING, Total Saving = SAVINGS; (b) My Balance = CHECKING + SAVINGS, Total Saving = SAVINGS.
   **Answer (PO):** (a). My Balance is the money available for spending; savings are shown separately.
2. What does a brand-new customer see before the accounts exist?
   Options: (a) 200 with zeros; (b) 200 with `status: PENDING`; (c) 404.
   **Answer (PO):** (b). Zeros would be a lie, and 404 is an error, which Product explicitly does not want.
3. How are amounts transported so that no floating point is involved?
   Options: (a) JSON strings `"1250.50"`; (b) integer minor units (cents).
   **Answer (PO):** (a). Readable in logs and API docs, exact, and easy to format.
4. Can a balance be negative?
   **Answer (PO):** Not in this ticket: no operation debits accounts yet. Revisit with the transfers ticket.
5. Should the cards follow the display-currency preference (Settings → Preferences)?
   **Answer (PO, review Q1):** No. This ticket always shows USD with `$` and fixed `en-US` grouping; applying the
   preference is out of scope.
6. How long does the UI poll while the summary is `PENDING` (e.g. if the event went to the DLT)?
   **Answer (PO, review Q2):** every 2 seconds for at most 30 seconds, then the error state with a Retry button (AC12, AC10).
7. How is `PENDING` detected?
   **Answer (tech lead, review Q3):** `READY` only when both the CHECKING and the SAVINGS account exist; anything
   else is `PENDING` (AC11).
8. What does the UI show while the first request is loading?
   **Answer (PO, review Q4):** a loading placeholder in each card, distinct from the "Setting up your accounts…" text.
9. Which errors does the API contract list besides 401?
   **Answer (tech lead, review Q5):** 502/503 from the gateway when account-service is unavailable; the UI treats
   every non-2xx response the same way (AC10).
10. How does polling relate to the existing 404 retry of the web client (`api/queryClient.ts`)?
    **Answer (tech lead, review Q6):** the summary never returns 404 for "not provisioned yet"; polling is a
    query-specific `refetchInterval` while `status === "PENDING"`, separate from the 404 retry.
11. How are the gateway route and the error and one-account cases tested?
    **Answer (tech lead, review Q7–Q9):** new AC10 (UI error state) and AC11 (one account → `PENDING`); AC1 runs
    through the gateway, which also covers the new route in the smoke test.
12. Is AC9 testable as one behaviour, and how are balances seeded?
    **Answer (tech lead, review Q10–Q11):** AC9 covers only the pending display; polling and the 30 s cap move to
    AC12. AC2 also sets SAVINGS to 0.10 to pin trailing zeros and plain notation. Integration tests seed balances
    with JDBC, since no operation can change a balance yet.
