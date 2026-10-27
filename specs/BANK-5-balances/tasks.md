# BANK-5: Account balances on the Accounts page — Tasks

Each task is roughly one commit that compiles and passes `./gradlew test` (and `npm test` for web tasks) on its
own. See [plan.md](plan.md) for the design (D1–D8) and [spec.md](spec.md) for the ACs.

- [x] **T1** — Derive the account summary from a user's accounts
  - Files: `services/account-service/src/main/java/com/bankdash/account/domain/AccountSummary.java`, `domain/AccountRepository.java` (`findByUser`), `infra/JdbcAccountRepository.java` (`findByUser`), test `domain/AccountSummaryTest.java`
  - Covers: AC1, AC3, AC4, AC11 (the rules; the endpoint comes in T2)
  - Tests: `AccountSummaryTest.ac1_readyWithCheckingAsMyBalanceAndSavingsAsTotalSaving`, `ac3_zeroBalancesAreReady`, `ac4_noAccountsIsPending`, `ac11_onlyCheckingIsPending`, `ac11_onlySavingsIsPending`

- [x] **T2** — Serve `GET /accounts/summary` with amounts as exact strings
  - Files: `app/AccountService.java` (`summary`), `api/AccountController.java` (new, `AccountSummaryResponse`), test `api/AccountSummaryApiIT.java`
  - Covers: AC1, AC2, AC3, AC4, AC5, AC6, AC11
  - Tests: `ac1_returnsCheckingAndSavingsBalances`, `ac2_amountsAreExactJsonStrings`, `ac3_newCustomerHasZeroBalances`, `ac4_unknownCustomerIsPendingWithNullAmounts`, `ac5_eachCustomerSeesOnlyTheirOwnBalances`, `ac6_requestWithoutTokenIs401ProblemDetail`, `ac6_requestWithInvalidTokenIs401ProblemDetail`, `ac11_onlyCheckingAccountIsPending`

- [x] **T3** — Route `/api/accounts/**` through the gateway
  - Files: `services/api-gateway/src/main/resources/application.yml`, `services/api-gateway/src/test/java/com/bankdash/gateway/GatewayIT.java`
  - Covers: AC1 (through the gateway), AC6
  - Tests: `GatewayIT.ac1_accountRequestsGoToAccountService`, `GatewayIT.ac6_accountsWithoutTokenIs401`, `GatewayIT.ac6_accountsWithInvalidTokenIs401`

- [x] **T4** — Add the web summary API and the USD string formatter
  - Files: `apps/web/src/api/accounts.ts`, `apps/web/src/accounts/money.ts`, `apps/web/src/accounts/money.test.ts`, `apps/web/src/test/users.ts` (`janeSummary`), `apps/web/src/test/renderWithAuth.tsx` (default `GET /api/accounts/summary`)
  - Covers: AC7, AC8 (formatting)
  - Tests: `it('AC7: formats 1250.50 as $1,250.50')`, `it('AC7: formats 300.00 and 0.00')`, `it('AC8: formats 12345678901234.89 exactly')`, `it('groups at every thousand')` (999.99, 1000.00), `it('rejects malformed amounts')`

- [x] **T5** — Show My Balance and Total Saving on the Accounts page
  - Files: `apps/web/src/accounts/queries.ts` (`useAccountSummary` with loading / ready / pending / error and `retry`, no polling yet), `apps/web/src/accounts/KpiCard.tsx`, `apps/web/src/accounts/BalanceKpis.tsx`, `apps/web/src/pages/AccountsPage.tsx`, `apps/web/src/pages/AccountsPage.test.tsx`
  - Covers: AC7, AC8, AC9, AC10
  - Tests: `it('shows a loading placeholder until the first response')`, `it('AC7: shows the balances in USD')`, `it('AC8: shows a very large balance exactly')`, `it('AC9: shows that the accounts are being set up')`, `it('AC10: shows an error with Retry on a 5xx response')`, `it('AC10: shows an error with Retry on a network error')`, `it('AC10: Retry requests the summary again')`

- [x] **T6** — Poll every 2 seconds while pending, for at most 30 seconds
  - Files: `apps/web/src/accounts/queries.ts` (`refetchInterval`, `pendingSince`, timeout, `retry` resets it), `apps/web/src/pages/AccountsPage.test.tsx`
  - Covers: AC12, AC10 (the error state after the timeout)
  - Tests: `it('AC12: polls every 2 seconds and stops once READY')`, `it('AC12: stops polling after 30 seconds and shows the error')`, `it('AC12: Retry after the timeout starts polling again')`

- [x] **T7** — Document the balance summary in CLAUDE.md
  - Files: `CLAUDE.md`
  - Covers: — (keeps the repository structure accurate: account-service now also serves the balance summary)
  - Tests: none (docs only); `./gradlew build` and `npm test` stay green

## AC → tasks coverage

| AC | Tasks |
|---|---|
| AC1 | T1, T2, T3 |
| AC2 | T2 |
| AC3 | T1, T2 |
| AC4 | T1, T2 |
| AC5 | T2 |
| AC6 | T2, T3 |
| AC7 | T4, T5 |
| AC8 | T4, T5 |
| AC9 | T5 |
| AC10 | T5, T6 |
| AC11 | T1, T2 |
| AC12 | T6 |

Every AC (AC1–AC12) is covered by at least one task.
