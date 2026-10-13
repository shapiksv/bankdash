---
name: step
description: Implement one step of docs/architecture.md §5 end to end — branch, plan, wait for approval, commits with tests, ✅ checks, smoke test, draft PR.
argument-hint: <step-number>
disable-model-invocation: true
---

# Implement Step $ARGUMENTS

Implement **Step $ARGUMENTS** of `docs/architecture.md` §5, and nothing else. Follow the phases below in order. If any check fails, stop and report it instead of working around it.

## 0. Validate the argument

`$ARGUMENTS` must be a single integer that matches a numbered step in §5 of `docs/architecture.md`. If it is empty, not a number, or not a step, stop and say so.

## 1. Read the context

- `CLAUDE.md` (commands, conventions, ports, infrastructure).
- All of `docs/architecture.md`: §1–4 define the services, events and flows the step builds on; the Step $ARGUMENTS section of §5 is the scope; §6–7 list deliberate simplifications and known tech debt.
- Any ADR in `docs/adr/` the step touches, and the existing code the step extends.

Scope rule: anything that belongs to a later step is out of scope. Note it as a follow-up for the PR instead of building it.

## 2. Preflight

Run these and stop on the first failure, showing the output:

1. `git status --porcelain` must be empty.
2. `git switch main`, `git fetch origin`, then compare `main` with `origin/main`:
   - up to date → continue;
   - behind only → `git pull --ff-only`;
   - ahead or diverged → stop and ask what to do.
3. Create the branch `step-$ARGUMENTS-<short-name>`, where `<short-name>` is the step title from §5 in kebab-case, at most about three words (for example `step-3-profile-kafka`). If the branch already exists locally or on `origin`, stop and ask.

## 3. Present the plan, then STOP

Present a plan that covers:

- **Modules and files**: new and changed, grouped by module (`libs/*`, `services/*`, `apps/web`, `infra/*`).
- **Endpoints**: method, path through the gateway (`/api/...`), auth requirement, request/response shape, error cases (ProblemDetail status codes).
- **Kafka**: topics and their `.DLT` topics (created in `infra/kafka`), event names, versions and payloads, producers (via the outbox) and consumers (idempotent via `processed_event`).
- **Schema**: Flyway migrations per service.
- **Key design decisions**, each with its reasoning and the alternative you rejected.
- **Tests**: mapped one by one to the **Tests** bullets of Step $ARGUMENTS (unit, Testcontainers integration, web).
- **Verification**: how each ✅ check of the step will be shown, and the curl smoke-test script you will run.
- **Commit order**: a numbered list of small, logical commits.

Then end your turn. **Do not write code, edit files or commit anything until the user replies "approved".** Any other reply is feedback: revise the plan, present it again, and stop again.

## 4. Implement (after "approved")

- Follow the planned commit order. Each commit is small and logical and **contains the tests for the code it adds**.
- Before each commit, run the tests of the affected modules (`./gradlew :<module>:test`, and `cd apps/web && npm test` for web changes) and make sure they pass.
- Follow every convention in `CLAUDE.md`: transactional outbox for events, Flyway for schema, ProblemDetail errors, `com.bankdash.<svc>.{api,domain,app,infra}` packages, versions in `gradle/libs.versions.toml`, Testcontainers only through `testFixtures(project(":libs:test-support"))`.
- Commit messages: English, imperative subject, a short body when the reason is not obvious, plus the Co-Authored-By trailer Claude Code asks for.
- Keep a running list of every deviation from the approved plan and why it happened.

## 5. Verify and show the results

Show the real output of each check; never claim a result you did not see.

1. **✅ checks of Step $ARGUMENTS**: `./gradlew build` (compiles and runs all tests). If `apps/web` changed: `cd apps/web && npm test && npm run build && npm run lint`.
2. **Manual smoke test with the services running**:
   - `docker compose up -d --wait`, and create the Kafka topics through `infra/kafka` if the step needs them.
   - Start auth-service, the gateway and every service the step touches with `./gradlew :services:<svc>:bootRun` as background processes.
   - Wait until each one's `/actuator/health` reports `UP`.
   - Run the step's flow with `curl` **through the gateway on `:8080`**: sign up, log in, use the access token, then call the step's endpoints. Verify asynchronous effects (Kafka events) through the resulting API state, retrying briefly where the flow is eventually consistent.
   - Print every request and its response (status and body).
   - Stop all `bootRun` processes afterwards.
3. If something fails, fix it in a new commit, re-run the affected checks, and record it as a deviation.

## 6. Push and open a draft PR

- `git push -u origin step-$ARGUMENTS-<short-name>`.
- `gh pr create --draft --base main --title "Step $ARGUMENTS: <step title>"` with a body that contains:
  - **Summary** of what the step adds;
  - **Verification**: the ✅ checks and an excerpt of the smoke-test output;
  - **Deviations from the plan**, with reasons;
  - **Follow-ups** that were out of scope;
  - the Claude Code footer.
- **Do not merge.**
- Finish with the PR URL and a short summary of the deviations from the plan.
