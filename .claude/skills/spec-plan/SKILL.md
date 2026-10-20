---
name: spec-plan
description: Turn a reviewed spec (specs/<TICKET>/spec.md) into plan.md and tasks.md, then stop for approval. No code.
argument-hint: <spec-folder, e.g. specs/BANK-4-cards>
disable-model-invocation: true
---

# Plan the spec in $ARGUMENTS

Produce `$ARGUMENTS/plan.md` and `$ARGUMENTS/tasks.md`. Do not write production code or tests, and do not create a branch.

## 0. Validate the argument

`$ARGUMENTS` must be a folder under `specs/` (not `specs/_template`) that contains `spec.md`. If it is empty or does not exist, stop and say so.

If the spec's **Open questions** section still contains unresolved questions, stop and list them: they must be answered in the spec first (suggest `/spec-review $ARGUMENTS`).

## 1. Read the context

- `$ARGUMENTS/spec.md` — the source of truth.
- `CLAUDE.md`, `docs/architecture.md`, the relevant ADRs in `docs/adr/`.
- The existing code the ticket extends.

If the spec is wrong, incomplete or conflicts with the architecture in a way that affects the plan, stop and ask instead of filling the gap yourself.

## 2. Write plan.md

`$ARGUMENTS/plan.md` with these sections:

- **Summary**: one paragraph, linking to `spec.md`.
- **Design decisions**: each decision with its reasoning, the alternative rejected, and the ACs it serves.
- **Modules and files**: new and changed files, grouped by module (`libs/*`, `services/*`, `apps/web`, `infra/*`).
- **Endpoints**: method, gateway path (`/api/...`), auth, request/response, ProblemDetail error cases, gateway route changes. Must match the spec's API contract exactly.
- **Events**: topics (and their `.DLT`, added to `infra/kafka/topics.txt`), payload records in `libs/events`, producers via `Outbox.publish`, consumers via `IdempotentConsumer.process`.
- **Migrations**: Flyway files per service (`V<n>__<name>.sql`), with the DDL in outline, including `outbox_event` / `processed_event` where a service starts producing or consuming.
- **Test strategy**: unit, Testcontainers integration and web tests, and which ACs each covers.
- **Smoke test**: the curl flow through the gateway that demonstrates the ticket.
- **Risks / follow-ups**: anything out of scope worth noting.

## 3. Write tasks.md

`$ARGUMENTS/tasks.md`: a numbered checklist of small, ordered tasks, each roughly one commit that compiles and passes its tests on its own:

```markdown
- [ ] **T1** — <imperative title>
  - Files: <paths>
  - Covers: AC1, AC3
  - Tests: <test classes / methods, named after the ACs>
```

Infrastructure and refactoring tasks with no AC say `Covers: —` and why they are needed. **Every AC of the spec must be covered by at least one task**; end tasks.md with an `AC → tasks` coverage table and confirm none is missing.

## 4. STOP

Show a short summary of the plan (decisions, task list, AC coverage) and end your turn. **Do not implement anything until the user explicitly approves the plan** ("approved"). Any other reply is feedback: revise plan.md / tasks.md, show the summary again and stop again.

Do not commit plan.md and tasks.md here; `/spec-implement` commits them as the first commit on the ticket branch.
