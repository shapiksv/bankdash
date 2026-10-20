---
name: spec-review
description: Review a ticket spec (specs/<TICKET>/spec.md) against CLAUDE.md and docs/architecture.md and report numbered questions. Read-only — never edits the spec or writes code.
argument-hint: <spec-folder, e.g. specs/BANK-4-cards>
disable-model-invocation: true
allowed-tools: Read, Grep, Glob, Bash(git status:*), Bash(git log:*), Bash(ls:*)
---

# Review the spec in $ARGUMENTS

This is a **read-only** review. Do not edit `spec.md` or any other file, do not create files, and do not write code. The output is a list of questions for the user.

## 0. Validate the argument

`$ARGUMENTS` must be a folder under `specs/` (not `specs/_template`) that contains `spec.md`. If it is empty or does not exist, stop and say so.

## 1. Read the context

- `$ARGUMENTS/spec.md`, every section.
- `CLAUDE.md` (conventions, ports, infrastructure).
- `docs/architecture.md` (services and their boundaries, events, sync vs async, simplifications, tech debt) and any ADR in `docs/adr/` the spec touches.
- `specs/_template/spec.md`, to check that no section is missing.
- The existing code the spec extends (controllers, events in `libs/events`, migrations, `infra/kafka/topics.txt`), enough to spot conflicts with what already exists.

## 2. Check

Look for:

1. **Ambiguities**: vague words ("fast", "valid", "appropriate"), undefined terms, unspecified formats, limits, ordering, rounding, time zones, currencies.
2. **Contradictions**: between sections (e.g. an AC vs the API contract, Events vs Data model), or with the existing code.
3. **Missing ACs**: behaviour stated in Scope, User stories, API contract, Events or Edge cases that no AC covers; missing negative paths (401/403/404/409/422), authorization (another user's resource), idempotency, concurrency, DLT behaviour.
4. **Untestable ACs**: no observable Then, several behaviours in one AC, outcomes that depend on timing without a bound.
5. **Conflicts with the architecture / CLAUDE.md**: cross-service table access, synchronous calls between business services, events sent without the outbox, consumers that are not idempotent, business outcomes modelled as exceptions, topics or `.DLT` topics missing from `infra/kafka/topics.txt`, schema changes without Flyway, errors that are not ProblemDetail, CORS outside the gateway, wrong service ownership.
6. **Template gaps**: empty or missing sections, unresolved Open questions.

## 3. Report

Output numbered questions (Q1, Q2, …), grouped under the six headings above (omit empty groups). Each question:

- cites the spec section and AC id (or the architecture section / file) it concerns;
- states the problem in one or two sentences;
- where useful, proposes an answer or default for the user to accept or reject.

End with a one-line verdict: **ready for `/spec-plan`** or **needs changes** (and how many blocking questions). Then stop.
