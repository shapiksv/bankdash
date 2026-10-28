---
name: spec-verify
description: Build the AC → test → status → evidence traceability table for an implemented spec, flag untested ACs, then push the ticket branch and open a draft PR with the table.
argument-hint: <spec-folder, e.g. specs/BANK-4-cards>
disable-model-invocation: true
---

# Verify the spec in $ARGUMENTS

## 0. Validate the argument

`$ARGUMENTS` must be a folder under `specs/` containing `spec.md` and `tasks.md`. The current branch must be the ticket branch (the spec folder's name, e.g. `BANK-4-cards`), not `main`, with a clean working tree. Otherwise stop and say so.

## 1. Collect the ACs and tests

- List every AC id of `$ARGUMENTS/spec.md` (skip ACs marked "Removed").
- Find the tests that reference each AC: search test sources (`services/*/src/test`, `libs/*/src/test*`, `apps/web/src/**/*.test.ts(x)`) for the AC id in method names, `@DisplayName` and `it(...)`/`test(...)` titles, case-insensitively (`ac3_`, `AC3:`).
- Find the e2e tests: `test(...)` titles in `apps/e2e/tests/**/*.spec.ts` that start with the ticket id and name the AC, e.g. `'BANK-5 AC9: ...'` or `'BANK-5 AC9 AC11: ...'`. Match the ticket id too: `AC9` of another ticket does not count.
- Check that every task in `tasks.md` is ticked.

## 2. Run the checks

Show the real output; never claim a result you did not see.

- `./gradlew build`, and `cd apps/web && npm test` if web tests exist for the ticket.
- If e2e tests exist for the ticket: `cd apps/e2e && npm run e2e -- --grep "<TICKET> "` (the trailing space keeps `BANK-1 ` from matching `BANK-12`). It starts its own stack on the e2e ports and stops only what it started; if it aborts because a port is taken, report the process it names, never stop it, and mark the e2e tests ⏭ not run with that reason. Save the output to `$ARGUMENTS/e2e-test.log` (git-ignored).
- Read each test's result from `build/test-results/**/TEST-*.xml` (JUnit), the Vitest output and `apps/e2e/test-results/results.json` (Playwright).
- If `$ARGUMENTS/smoke-test.log` exists from `/spec-implement`, use it as extra evidence; otherwise say the smoke test was not run.

## 3. Traceability table

| AC | Test(s) | Status | Evidence |
|---|---|---|---|
| AC1 — short name | `ProfileIT.ac1_createsProfile` | ✅ pass | JUnit report; smoke test step 2 (`201`) |
| AC2 — ... | — | ⚠️ no test | |
| AC9 — Pending state in the UI | `AccountsPage.test.tsx` › `AC9: ...`; (e2e) `bank-5-balances.spec.ts` › `BANK-5 AC9 AC11: ...` | ✅ pass | Vitest; Playwright report |

- **Test(s)**: e2e tests are marked `(e2e)` and listed next to the unit and integration tests of the same AC.
- **Status**: ✅ pass, ❌ fail, ⚠️ no test, ⏭ skipped. One failing test of an AC, e2e included, makes the AC ❌.
- **Evidence**: the test report entry and, where it applies, the smoke-test request/response that demonstrates the AC.
- Flag every AC without a test and every failing test prominently above the table. Do not invent coverage: a test counts only if it actually asserts the AC's Then.
- A UI AC (one about what the user sees or does in the browser) that has unit tests but no e2e test is not a gap that blocks the PR. List it under **Gaps / follow-ups** with a pointer to `/ui-test $ARGUMENTS`.

If any AC is untested or failing, stop and ask whether to fix it (via `/spec-implement`) or open the PR anyway with the gaps listed.

## 4. Push and open a draft PR

- Set the spec's **Status** to `Verified` (or `Implemented` if gaps remain) and commit it with the message `<TICKET>: verify`.
- `git push -u origin <branch>`.
- `gh pr create --draft --base main --title "<TICKET>: <spec title>"` with a body containing:
  - **Summary** and a link to `$ARGUMENTS/spec.md`;
  - **Traceability**: the full table;
  - **Checks**: build/test results, the e2e result (passed/failed counts, or why it did not run) and a smoke-test excerpt;
  - **Spec changes during implementation**, with reasons;
  - **Gaps / follow-ups**;
  - the Claude Code footer.
- **Do not merge.** Finish with the PR URL and the table.
