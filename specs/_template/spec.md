# <TICKET-ID>: <Title>

<!--
Copy this folder to specs/<TICKET-ID>-<short-name>/ (e.g. specs/BANK-4-cards/) and fill in every section.
The spec is the source of truth: plan.md, tasks.md, code and tests must trace back to it.
Write "None" rather than deleting a section.
-->

- **Status:** Draft | Reviewed | Planned | Implemented | Verified
- **Services:** <e.g. account-service, apps/web>

## Context

Why this ticket exists: the user or business problem, the screens of `docs/ui-analysis.md` it serves, and the parts of `docs/architecture.md` it builds on.

## Scope

What this ticket delivers, as a short bullet list.

## Out of scope

What this ticket deliberately does not deliver, and which ticket (if any) will.

## User stories

- As a <role>, I want <capability>, so that <benefit>.

## Acceptance criteria

Each AC is observable and testable. Tests reference AC ids (e.g. `ac1_createsCardForOwnAccount`). Never renumber an AC; mark removed ones as "Removed".

- **AC1** — <short name>
  - **Given** <precondition>
  - **When** <action>
  - **Then** <observable outcome>
- **AC2** — ...

## API contract

Paths as seen through the gateway (`/api/...`).

| Method | Path | Auth | Request | Response | Errors (ProblemDetail) |
|---|---|---|---|---|---|
| | | | | | |

Request/response examples:

```json
```

## Events

| Topic (`.v1`) | Producer (via outbox) | Consumer(s) (idempotent) | Payload | DLT |
|---|---|---|---|---|
| | | | | |

## Data model

Tables, columns, constraints and indexes per service database, and the Flyway migrations that create them.

## Non-functional

Security (who may access what), performance, consistency (sync vs eventual), idempotency, concurrency, observability.

## Edge cases & errors

| Case | Expected behaviour | HTTP status / event |
|---|---|---|
| | | |

## Open questions

Numbered. Every question must be resolved (answer recorded here) before `/spec-plan`.

1. ...
