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
- Check that every task in `tasks.md` is ticked.

## 2. Run the checks

Show the real output; never claim a result you did not see.

- `./gradlew build`, and `cd apps/web && npm test` if web tests exist for the ticket.
- Read each test's result from `build/test-results/**/TEST-*.xml` (JUnit) and the Vitest output.
- If `$ARGUMENTS/smoke-test.log` exists from `/spec-implement`, use it as extra evidence; otherwise say the smoke test was not run.

## 3. Traceability table

| AC | Test(s) | Status | Evidence |
|---|---|---|---|
| AC1 — short name | `ProfileIT.ac1_createsProfile` | ✅ pass | JUnit report; smoke test step 2 (`201`) |
| AC2 — ... | — | ⚠️ no test | |

- **Status**: ✅ pass, ❌ fail, ⚠️ no test, ⏭ skipped.
- **Evidence**: the test report entry and, where it applies, the smoke-test request/response that demonstrates the AC.
- Flag every AC without a test and every failing test prominently above the table. Do not invent coverage: a test counts only if it actually asserts the AC's Then.

If any AC is untested or failing, stop and ask whether to fix it (via `/spec-implement`) or open the PR anyway with the gaps listed.

## 4. Push and open a draft PR

- Set the spec's **Status** to `Verified` (or `Implemented` if gaps remain) and commit it with the message `<TICKET>: verify`.
- `git push -u origin <branch>`.
- `gh pr create --draft --base main --title "<TICKET>: <spec title>"` with a body containing:
  - **Summary** and a link to `$ARGUMENTS/spec.md`;
  - **Traceability**: the full table;
  - **Checks**: build/test results and a smoke-test excerpt;
  - **Spec changes during implementation**, with reasons;
  - **Gaps / follow-ups**;
  - the Claude Code footer.
- **Do not merge.** Finish with the PR URL and the table.
