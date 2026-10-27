---
name: code-review
description: Review a pull request in a fresh-context subagent (tests first, then money/security/concurrency, contracts, spec deviations) and report numbered findings. Read-only; posts to GitHub only after the user agrees.
argument-hint: <PR number, e.g. 5>
disable-model-invocation: true
allowed-tools: Agent, Read, Grep, Glob, AskUserQuestion, Bash(gh pr view:*), Bash(gh pr diff:*), Bash(gh pr checks:*), Bash(gh repo view:*), Bash(gh api:*), Bash(git log:*), Bash(git show:*), Bash(git diff:*), Bash(git status:*), Bash(git fetch:*), Bash(ls:*)
---

# Review PR #$ARGUMENTS

This is a **read-only** review. Never change code, never check out the PR branch, never commit. Nothing is posted to GitHub without the user's explicit agreement (step 3).

## 0. Validate the argument

`$ARGUMENTS` must be a single PR number. If it is empty or not a number, stop and say so. Run `gh pr view $ARGUMENTS --json number,title,state,headRefName,baseRefName,headRefOid`; if it fails, stop and report the error.

## 1. Run the review in a subagent

The reviewer must not have seen how the code was written, so do **not** review in this conversation. Launch one `general-purpose` subagent with the Agent tool and give it a self-contained prompt: the PR number, the repository root, the review order and output format below, and the read-only rule. Do not pass your own opinions of the code. Wait for its report.

The subagent must:

1. **Read the context**
   - The PR: `gh pr view <n>` (description, base branch) and `gh pr diff <n>`. For a large diff, list the files first and read them one by one. Read files in full from the head commit (`git show <headRefOid>:<path>`, after `git fetch origin pull/<n>/head` if needed) whenever the diff alone hides the surrounding code.
   - The spec: the `specs/<TICKET>-*/` folder whose ticket id appears in the PR title or branch name (`spec.md`, `plan.md`, `tasks.md`). If there is none, say so and skip the spec checks.
   - `CLAUDE.md` and `docs/architecture.md`.
2. **Review in this order**, spending the most effort on the first items:
   1. **Tests.** Would any test still pass if the code it covers were broken (no assertion, asserts only status 200, mocks the thing under test, tests the mock, assertion too weak)? Is every spec AC covered by a test whose name references it? Are failure paths and edge cases tested, not only the happy path?
   2. **Money, security, transactions, concurrency, idempotency, outbox/consumers.** Rounding and currency handling, authorization (does the user own the resource? ids taken from the token, not the request), validation, secrets and PII in logs, `@Transactional` boundaries, lost updates and races (locking, unique constraints), duplicate delivery, `Outbox.publish` inside the business transaction, `IdempotentConsumer.process` around handlers, DLT behavior.
   3. **Contracts that are hard to change later.** REST API (paths, status codes, field names, `ProblemDetail` errors), events (payload shape, topic names, `infra/kafka/topics.txt` incl. `.DLT`), Flyway migrations. An already-merged migration must never be edited: compare against the base branch (`git diff <base>...<head> -- '**/db/migration/**'`, and check that no file present on the base is modified or renamed). Also check migration safety (locks, defaults, backfills).
   4. **Deviations** from the spec (missing, extra or changed behavior) and from the CLAUDE.md conventions (English only, package layout, versions in the catalog, each service owns its database, no direct `KafkaTemplate.send`, Testcontainers only in test fixtures, and so on).
   5. **Everything else, briefly**: naming, duplication, dead code, error handling, frontend issues, small cleanups.
3. **Verify before reporting.** Open the code for every candidate finding and confirm it. Drop what you cannot confirm or mark it as a question. Do not report style preferences the codebase does not follow itself. Do not run builds or tests that write outside the scratchpad, and do not modify any file.

Required report format from the subagent:

```
## Must fix before merge
1. [high|medium] path/File.java:42 — <what is wrong and why it matters>
   Fix: <concrete suggestion>

## Can be tech debt
2. [medium|low] path/File.java:10 — <issue>
   Fix: <suggestion>

## Not covered / assumptions
<spec missing, files not reviewed, anything the reviewer could not verify>
```

Findings are numbered continuously across both sections, ordered by the review order above and then by severity. Every finding has a `file:line` on the **head** version of the file, a severity, and a suggested fix. If nothing is wrong, the report says so explicitly.

## 2. Present the report

Show the user the subagent's findings as received, keeping the two sections. Do not soften, drop or add findings. If a finding looks wrong to you, say so next to it instead of removing it.

## 3. Ask before posting to GitHub

Ask the user with AskUserQuestion whether to post the findings to PR #$ARGUMENTS (options: post all, post only "must fix", do not post). Post nothing unless they agree.

If they agree, post the chosen findings as inline comments in **one** review:

- Build a single `POST /repos/{owner}/{repo}/pulls/$ARGUMENTS/reviews` call with `gh api` (owner/repo from `gh repo view --json nameWithOwner`), `commit_id` = the `headRefOid` from step 0, `event` = `COMMENT`, a short summary `body`, and a `comments` array with `path`, `line`, `side: "RIGHT"` and `body` (`**[severity] #N** ...` plus the suggested fix) per finding.
- A finding whose line is not part of the diff cannot be an inline comment: put it into the review `body` instead and tell the user.
- Never use `APPROVE` or `REQUEST_CHANGES`, and never post more than one review.
- Report the review URL afterwards.
