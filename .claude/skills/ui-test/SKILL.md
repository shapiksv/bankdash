---
name: ui-test
description: Find a spec's UI acceptance criteria, write the missing Playwright e2e tests in apps/e2e, run them on the e2e stack and report AC → e2e test → result.
argument-hint: <spec-folder, e.g. specs/BANK-5-balances>
disable-model-invocation: true
---

# End-to-end UI tests for $ARGUMENTS

Cover every UI acceptance criterion of `$ARGUMENTS/spec.md` with a Playwright test in `apps/e2e`, run them, and report the result per AC. Tests check the app against the spec: never change application code or the spec to make a test pass.

## 0. Validate the argument

- `$ARGUMENTS` must be a folder under `specs/` (not `specs/_template`) containing `spec.md`. The ticket id is the folder name up to the second `-` (`specs/BANK-5-balances` → `BANK-5`). Otherwise stop and say so.
- The working tree must be clean. Tests are committed on the ticket branch (the folder's name, e.g. `BANK-5-balances`). If the current branch is `main` or another branch, stop and ask which branch to use.

## 1. Read the context

- `$ARGUMENTS/spec.md`, plus `plan.md` if it exists.
- The **End-to-end tests** section of `CLAUDE.md`: ports, naming, data seeding and the rules for `page.route` and `page.clock`.
- Everything in `apps/e2e/support/` (`fixtures.ts`, `api.ts`, `db.ts`, `env.ts`) and the existing `apps/e2e/tests/*.spec.ts`. They show the style to follow; reuse their helpers.
- The `apps/web/src` pages and components the ACs are about. They give the roles, labels and texts to locate elements by.

## 2. Select the UI ACs

Go through every AC (skip those marked "Removed"). An AC is a **UI AC** when its Given/When/Then is about what a user sees or does in the browser: a page, card, dialog, button, message, "in the UI". Backend-only ACs (an endpoint's status or body, events, idempotency, database state) are **not UI**: integration tests cover them, and e2e tests do not repeat them. A backend AC whose effect is visible on a page (e.g. "only one account exists → PENDING") may share the e2e test of the UI AC that shows it (`BANK-5 AC9 AC11: ...`), but only when that test really asserts it.

If it is unclear whether an AC is a UI AC, or what exactly the UI must show, list it as a question and ask before writing tests for it. Do not guess.

## 3. Find the existing e2e tests

An AC is covered when a `test(...)` title in `apps/e2e/tests/**/*.spec.ts` starts with the ticket id and names the AC (`'BANK-5 AC9: ...'`, `'BANK-5 AC9 AC11: ...'`) **and** the test asserts the AC's Then. A title alone does not count; mention a test whose title promises more than it checks.

## 4. Write the missing tests

Put them in `apps/e2e/tests/<spec folder name in lower case>.spec.ts` (e.g. `bank-5-balances.spec.ts`), creating the file if needed with a first comment line that links the spec. Rules:

- **Names:** `test('<TICKET> AC<n>: <what the user sees>', ...)`; a test covering several ACs names them all (`<TICKET> AC9 AC11: ...`).
- **One user per test:** use the `user` fixture (signed up through `page.request`, so the page starts logged in) and `api` for the same user. No state shared between tests, no fixed emails, no dependence on test order.
- **Set up through the real API** where an endpoint exists. Data without an API yet is written to that service's e2e database: add a small, documented function to `support/db.ts`, connecting only as that service's own user, plus its connection settings in `support/env.ts` if needed. **Never add test-only endpoints, flags or profiles to production code.**
- **Assert through the UI** what the AC's Then says, using accessible locators (`getByRole`, `getByLabel`, `getByText`, plus `data-testid` only where one already exists). If an element cannot be located without changing `apps/web`, stop and ask.
- **Fault injection only** with `page.route` (5xx, network errors). Everything else talks to the real backend.
- **Time** (polling, timeouts, expiry) with `page.clock`: `install()` before `goto`, then `fastForward`. The clock keeps running in real time, so keep waits after a `fastForward` short (see `bank-5-balances.spec.ts`). Never sleep for the real duration.
- For a time-based or "nothing is sent" assertion, temporarily break the app (e.g. change the interval constant), check that the test fails, and revert the change before going on. Mention the result in the report.

Then run `cd apps/e2e && npm run typecheck && npm run lint`.

## 5. Run them

- `cd apps/e2e && npm run e2e -- --grep "<TICKET> "` (the trailing space keeps `BANK-1 ` from matching `BANK-12`). It starts the e2e stack on its dedicated ports, runs the tests and stops only what it started. If it aborts because a port is taken, report the process it names and ask. **Never stop a process the script did not start.** If the user already runs the e2e stack (`npm run stack:up`), `npm run e2e` reuses it and leaves it running.
- Save the output to `$ARGUMENTS/e2e-test.log` (git-ignored; `/spec-verify` may use it). Read the per-test results from `apps/e2e/test-results/results.json`.
- **A failing test:** open its trace (`npx playwright show-trace <path from the output>`) or screenshot and decide:
  - the test is wrong (locator, timing, setup): fix it and run again;
  - the app does not do what the AC says: **stop**, report the AC, the expected and the actual behaviour with the trace path, and ask. Do not touch the app or the spec.
  - it passes on a rerun: it is flaky. Confirm with `--repeat-each=5`, fix the cause in the test, and never just retry.

## 6. Commit

On the ticket branch, commit the new and changed files of `apps/e2e` with the message `<TICKET>: e2e tests for AC<n>, AC<m>` and the Co-Authored-By trailer. Do not push.

## 7. Report

Show the real results; never claim a result you did not see.

| AC | E2E test | Result |
|---|---|---|
| AC7 — KPI cards in the UI | `bank-5-balances.spec.ts` › `BANK-5 AC7: ...` | ✅ pass |
| AC9 — Pending state | `bank-5-balances.spec.ts` › `BANK-5 AC9 AC11: ...` (new) | ✅ pass |
| AC1 — Summary endpoint | — | — not UI |
| AC10 — Error state | `...` (new) | ❌ fail: Retry shows no balances (trace: `test-results/.../trace.zip`) |

- **Result:** ✅ pass, ❌ fail, ⚠️ missing (a UI AC still without a test, with the reason), — not UI.
- Mark the tests written in this run as `(new)`, and list the mutation checks of step 4 with their outcome.
- Below the table: the commit, the command run with its passed/failed counts, the questions from step 2, and every failure that needs a decision.
