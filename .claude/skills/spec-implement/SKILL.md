---
name: spec-implement
description: Implement an approved spec's tasks.md in order on a ticket branch — small commits, AC-named tests, all checks and a smoke test through the gateway. Stops and asks if the spec is wrong.
argument-hint: <spec-folder, e.g. specs/BANK-4-cards>
disable-model-invocation: true
---

# Implement the spec in $ARGUMENTS

Implement `$ARGUMENTS/tasks.md`, in order, and nothing else. The spec is the source of truth; every change must trace back to an AC or to a task in `tasks.md`.

## 0. Validate the argument

`$ARGUMENTS` must be a folder under `specs/` containing `spec.md`, `plan.md` and `tasks.md`. If any is missing, stop and say so (suggest `/spec-plan $ARGUMENTS`). Only proceed if the user has approved the plan in this conversation or says so now.

## 1. Read the context

- `$ARGUMENTS/spec.md`, `plan.md`, `tasks.md`.
- `CLAUDE.md` and `docs/architecture.md`, plus any ADR the ticket touches.

## 2. Preflight

Stop on the first failure and show the output:

1. The working tree is clean, apart from untracked `plan.md` / `tasks.md` in `$ARGUMENTS`.
2. `git switch main`, `git fetch origin`, compare `main` with `origin/main`: up to date → continue; behind only → `git pull --ff-only`; ahead or diverged → stop and ask.
3. Create the branch named after the spec folder, e.g. `specs/BANK-4-cards` → `BANK-4-cards`. If it already exists locally or on `origin`, stop and ask.
4. First commit: `$ARGUMENTS/plan.md` and `tasks.md` (and `spec.md` if it changed), message `<TICKET>: plan and tasks`.

## 3. Implement task by task

For each task of `tasks.md`, in order:

- Implement exactly that task, with its tests in the same commit.
- **Test names reference AC ids**: methods like `ac3_rejectsTransferToForeignAccount()` (JUnit) or `it('AC3: rejects ...')` (Vitest). A test that covers several ACs names all of them (`ac1_ac2_...`). Use `@DisplayName("AC3: ...")` where a readable name helps.
- Follow every convention of `CLAUDE.md`: outbox for events, idempotent consumers, Flyway, ProblemDetail, package layout, versions in `gradle/libs.versions.toml`, Testcontainers only via `testFixtures(project(":libs:test-support"))`, topics and `.DLT` in `infra/kafka/topics.txt`.
- Run the tests of the affected modules (`./gradlew :<module>:test`; `cd apps/web && npm test` for web changes) and make sure they pass.
- Tick the task in `tasks.md` (`- [x]`) and commit both together. Message: `<TICKET> T<n>: <imperative title>`, a short body when the reason is not obvious, plus the Co-Authored-By trailer.

**If the spec is wrong, incomplete, ambiguous or contradicts the architecture or the code — STOP and ask.** Never silently deviate, invent behaviour, or "fix" the spec yourself. Explain the problem, propose options, and wait. Once the user decides, the spec (and plan/tasks if affected) is updated in its own commit (`<TICKET>: update spec — <reason>`) before the code follows it. The same applies when a task turns out to need a different design than plan.md: ask, then update plan.md in its own commit.

Keep a running list of every change to spec/plan/tasks made during implementation and why.

## 4. Checks

Show the real output of each; never claim a result you did not see.

1. `./gradlew build` (compiles and runs all tests).
2. If `apps/web` changed: `cd apps/web && npm test && npm run build && npm run lint`.

## 5. Smoke test through the gateway

- `docker compose up -d --wait` and `./infra/kafka/create-topics.sh`.
- Before starting anything, check the ports (`lsof -iTCP:<port> -sTCP:LISTEN`). **Never stop processes you did not start**; if a port is taken, run the stack on alternate ports (e.g. `--args='--server.port=18080 ...'`) and say so.
- Start auth-service, the gateway and every service the ticket touches with `./gradlew :services:<svc>:bootRun` in the background; wait until each `/actuator/health` is `UP`.
- Run the flow from plan.md with `curl` **through the gateway**: sign up, log in, then exercise the ticket's endpoints, covering the happy path and the main error cases of the ACs. Verify asynchronous effects through API state, retrying briefly where eventually consistent.
- Print every request and its response (status and body), noting which AC each demonstrates. Save the output to `$ARGUMENTS/smoke-test.log` (git-ignored; `/spec-verify` uses it as evidence).
- Stop the `bootRun` processes you started.

If anything fails, fix it in a new commit, re-run the affected checks, and record it.

## 6. Finish

Do not push or open a PR — that is `/spec-verify`. Report: commits made, check results, smoke-test summary, and every spec/plan/tasks change with its reason. Suggest `/spec-verify $ARGUMENTS`.
