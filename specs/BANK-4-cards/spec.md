# BANK-4: Cards and accounts for new customers

- **Status:** Reviewed
- **Services:** services/account-service (new), services/api-gateway, libs/events, infra/kafka, apps/web

## Context

A new customer signs up and sees an empty dashboard, which is a bad first impression. On successful
registration the customer must immediately get a CHECKING account, a SAVINGS account and one DEBIT card,
so they can start exploring the app.

The customer also needs a Credit Cards page (see `docs/ui-analysis.md`: Credit Cards, Dashboard → My Cards)
to view their cards, open card details, add a card, block a card and set or change the PIN.

Builds on `docs/architecture.md`: account-service owns accounts and cards (§1) and consumes
`auth.user-registered.v1` (§3), which auth-service already publishes since Step 3.

## Scope

1. On successful registration, account-service creates for the user:
  - a CHECKING account with zero balance,
  - a SAVINGS account with zero balance,
  - one DEBIT card linked to the CHECKING account, status INACTIVE, no PIN.
2. account-service generates card data: a 16-digit card number and an expiry date (end of the month,
   3 years from creation). Numbers are fake (learning project, no real payment network).
   Only the last 4 digits are stored (`docs/architecture.md` §6).
3. A card has: id, account id, type (DEBIT), holder name (from registration), last 4 digits,
   expiry (MM/YY), PIN hash, status.
4. Card status: INACTIVE (no PIN yet), ACTIVE, BLOCKED. A card whose expiry is in the past is
   reported as EXPIRED (derived on read, not stored).
5. Credit Cards page: list of the user's cards, card details, Add card, Block card, Set/Change PIN.
   Non-blocked cards come first, newest first, then blocked cards, newest first (CR-1).
6. Dashboard "My Cards" widget shows up to 2 of the user's non-blocked cards, newest first (CR-1).
7. Card numbers are always masked: only the last 4 digits are shown (`**** **** **** 5678`).
8. Blocking requires an explicit confirmation step in the UI.
9. PIN rules (validated on the backend; the frontend repeats them only for convenience).
   A PIN is rejected if it:
  - is not exactly 4 digits;
  - has all digits the same (0000, 1111, …);
  - is 4 consecutive ascending or descending digits (0123, 1234, …, 6789, 9876, …, 3210);
  - is a repeating pair ABAB (1212, 6969, 2020, …);
  - looks like a year between 1950 and 2029;
  - is on the keypad-pattern denylist: 2580, 0852, 1470, 0741, 3690, 0963, 1590, 0951, 3570, 0753, 1593, 3951;
  - equals the card's current PIN (on change).
10. A new card added by the user is created with a PIN and is ACTIVE immediately.
11. Infrastructure:
  - new `services/account-service` on port 8083 with its own `account` database (already in `infra/postgres/init.sql`);
  - gateway routes `/api/cards/**` to account-service;
  - `cards.status-changed.v1` and `cards.status-changed.v1.DLT` added to `infra/kafka/topics.txt`;
  - the `CardStatusChanged` payload record and topic constant added to `libs/events`.

## Out of scope

- Real card issuing and any payment network integration.
- Credit cards (only DEBIT cards in this ticket; credit cards are a future ticket).
- Unblocking a card (blocking is final, see Q1).
- Deleting a card (see Q3).
- Notification about a blocked card via notification-service (separate ticket). The UI still shows a
  local confirmation message (toast).
- Rate limiting of PIN changes (tracked in `docs/architecture.md` §7).
- PIN check against the user's date of birth (see Q4).
- Gateway route `/api/accounts/**` (comes with the Accounts ticket, together with its endpoints).

## User stories

- As a new customer, I want my accounts and a debit card to be created when I sign up, so that I can explore the app right away.
- As a customer, I want to see my cards with masked numbers, so that I can tell them apart without exposing card data.
- As a customer, I want to open a card's details, so that I can see its status, expiry and linked account.
- As a customer, I want to add a debit card to one of my accounts, so that I can use separate cards for different needs.
- As a customer, I want to set or change my card PIN, and get a clear reason if the PIN is rejected, so that my card stays secure.
- As a customer, I want blocking to ask for confirmation, so that I don't block a card by mistake.

## Acceptance criteria

- **AC1** — Accounts and a card are created on registration
  - **Given** a user registered and `auth.user-registered.v1` was published for them
  - **When** account-service consumes the event
  - **Then** the user has exactly one CHECKING and one SAVINGS account with balance 0.00, and one DEBIT
    card linked to the CHECKING account with status INACTIVE, no PIN and holder name = fullName from the event
- **AC2** — Duplicate registration event is ignored
  - **Given** AC1 already happened for a user
  - **When** the same `auth.user-registered.v1` event (same eventId) is consumed again
  - **Then** no additional accounts or cards are created
- **AC3** — List own cards
  - **Given** a user with cards and another user with cards
  - **When** the user calls `GET /api/cards`
  - **Then** the response contains only their own cards, each with a masked number showing only the last 4 digits
- **AC4** — Card details
  - **Given** a user owns a card
  - **When** they call `GET /api/cards/{id}`
  - **Then** the response is 200 with id, maskedNumber, holderName, expiry, status, type and accountId
- **AC5** — Someone else's card is not found
  - **Given** a card that belongs to another user
  - **When** the user calls any `/api/cards/{id}...` endpoint for it
  - **Then** the response is 404, identical to the response for a non-existent card
- **AC6** — Add a card
  - **Given** a user has a CHECKING and a SAVINGS account
  - **When** they call `POST /api/cards` with an accountType (CHECKING or SAVINGS) and a valid PIN
  - **Then** the response is 201 with the new card: type DEBIT, status ACTIVE, holder name = their registered
    full name, linked to their own account of that type
- **AC7** — Removed (see Q6: the request names an account type, so it always refers to the caller's own account;
  AC19 covers a missing account)
- **AC8** — Set the first PIN activates the card
  - **Given** a user owns an INACTIVE card without a PIN
  - **When** they call `PUT /api/cards/{id}/pin` with a valid PIN
  - **Then** the response is 204, the PIN is stored as a hash and the card becomes ACTIVE
- **AC9** — Change PIN
  - **Given** a user owns an ACTIVE card
  - **When** they call `PUT /api/cards/{id}/pin` with a valid PIN different from the current one
  - **Then** the response is 204 and the new PIN hash replaces the old one
- **AC10** — Weak PIN is rejected
  - **Given** a user owns a card
  - **When** they set a PIN that violates any rule from Scope §9 (examples: 0000, 1234, 9876, 1212, 1998, 2580, abcd, 123)
  - **Then** the response is 422 with a ProblemDetail naming the violated rule, and the PIN is unchanged
- **AC11** — New PIN equal to the current PIN is rejected
  - **Given** a user owns an ACTIVE card with a PIN
  - **When** they set the same PIN again
  - **Then** the response is 422 and the PIN is unchanged
- **AC12** — PIN cannot be changed on a blocked or expired card
  - **Given** a user owns a BLOCKED card or a card whose expiry is in the past
  - **When** they call `PUT /api/cards/{id}/pin`
  - **Then** the response is 409 and nothing changes
- **AC13** — Block a card
  - **Given** a user owns an ACTIVE or INACTIVE card
  - **When** they call `POST /api/cards/{id}/block`
  - **Then** the response is 204, the card status is BLOCKED and `cards.status-changed.v1` is published
- **AC14** — Block an already blocked card
  - **Given** a user owns a BLOCKED card
  - **When** they call `POST /api/cards/{id}/block`
  - **Then** the response is 409, no event is published
- **AC15** — Blocking needs confirmation in the UI
  - **Given** the user is on the Credit Cards page
  - **When** they click "Block card"
  - **Then** a confirmation dialog appears; the block request is sent only after "Confirm", and "Cancel" sends nothing
- **AC16** — Card limit
  - **Given** a user already has 5 non-blocked cards
  - **When** they call `POST /api/cards`
  - **Then** the response is 409 and no card is created
- **AC17** — Cards are shown in the UI
  - **Given** a logged-in user with cards
  - **When** they open the Credit Cards page and the Dashboard
  - **Then** the Credit Cards page lists all their cards in the AC20 order, and the Dashboard "My Cards" widget
    shows up to 2 of their non-blocked cards, newest first, never a blocked one; all with masked numbers (CR-1)
- **AC18** — Requests without a token are rejected
  - **Given** no access token or an invalid one
  - **When** any `/api/cards...` endpoint is called through the gateway
  - **Then** the response is 401 ProblemDetail and nothing changes
- **AC19** — Add a card before the accounts exist
  - **Given** a user whose `auth.user-registered.v1` event has not been processed yet (no accounts)
  - **When** they call `POST /api/cards` with any accountType
  - **Then** the response is 404 and no card is created
- **AC20** — Card order (CR-1)
  - **Given** a user with several non-blocked and blocked cards created at different times
  - **When** they call `GET /api/cards` (with or without `limit`)
  - **Then** the non-blocked cards come first, newest first, followed by the blocked cards, newest first;
    `limit` returns the first N cards of that order
- **AC21** — Missing or unknown account type (CR-1)
  - **Given** a logged-in user
  - **When** they call `POST /api/cards` without an accountType or with a value other than CHECKING or SAVINGS
  - **Then** the response is 400 ProblemDetail with detail "accountType must be CHECKING or SAVINGS"
    and no card is created

## API contract

Paths as seen through the gateway (`/api/...`). All endpoints require a valid access token; the user is
taken from the token (`sub`), never from the request.

| Method | Path | Request | Response | Errors (ProblemDetail) |
|---|---|---|---|---|
| GET | /api/cards | `?limit=` (optional, 1–100) | 200 `{ cards: [Card] }`, ordered as in AC20 | 401 |
| GET | /api/cards/{id} | — | 200 `Card` | 401, 404 |
| POST | /api/cards | `{ accountType, pin }` | 201 `Card` | 400 (missing or unknown accountType, detail "accountType must be CHECKING or SAVINGS"), 401, 404 (no account of that type yet), 409 (card limit), 422 (weak PIN) |
| PUT | /api/cards/{id}/pin | `{ pin }` | 204 | 401, 404, 409 (blocked / expired), 422 (weak PIN or same as current) |
| POST | /api/cards/{id}/block | — | 204 | 401, 404, 409 (already blocked) |

Card:

```json
{
  "id": "6f1c2a3e-7b1d-4c55-9a0e-2f3b4c5d6e7f",
  "accountId": "0a9b8c7d-6e5f-4a3b-2c1d-0e9f8a7b6c5d",
  "type": "DEBIT",
  "maskedNumber": "**** **** **** 5678",
  "holderName": "John Doe",
  "expiry": "10/29",
  "status": "ACTIVE"
}
```

Create card request:

```json
{ "accountType": "SAVINGS", "pin": "4826" }
```

Weak PIN error:

```json
{
  "type": "about:blank",
  "title": "Weak PIN",
  "status": 422,
  "detail": "PIN must not be a sequence of consecutive digits",
  "rule": "CONSECUTIVE_DIGITS"
}
```

## Events

| Topic (`.v1`) | Producer (via outbox) | Consumer(s) (idempotent) | Payload | DLT |
|---|---|---|---|---|
| auth.user-registered.v1 (exists) | auth-service | account-service (new consumer) | userId, email, fullName | auth.user-registered.v1.DLT |
| cards.status-changed.v1 (new) | account-service | none yet (notification-service in a later ticket) | cardId, userId, status | cards.status-changed.v1.DLT |

`cards.status-changed.v1` payload (inside the standard envelope with eventId, eventType, source, schemaVersion):

```json
{ "cardId": "6f1c2a3e-...", "userId": "c3d4...", "status": "BLOCKED" }
```

## Data model

account-service database, Flyway migrations:

- **account**: id (uuid, PK), user_id (uuid, not null), type (CHECKING | SAVINGS), currency (USD),
  balance (numeric(19,2), default 0), created_at. Unique (user_id, type).
- **card**: id (uuid, PK), user_id (uuid, not null), account_id (uuid, FK → account), type (DEBIT),
  holder_name, last4 (char(4)), expiry_month, expiry_year, pin_hash (nullable), status
  (INACTIVE | ACTIVE | BLOCKED), created_at, updated_at. Index on user_id.
- **customer**: user_id (uuid, PK), full_name (not null), created_at. Created from `auth.user-registered.v1`;
  the holder name of cards added later is taken from here (Q7).
- **outbox_event** and **processed_event**: from `libs/messaging`.

The unique (user_id, type) constraint on account is a second line of defence for AC2.

## Non-functional

- **Security:** a user never sees or changes another user's accounts or cards (404, not 403).
  Full card numbers never leave the backend and are not stored; only last4 is stored.
  PINs are stored as BCrypt hashes and never logged or returned.
- **Support:** blocking is hard to do by accident (confirmation dialog, AC15).
- **Compliance:** PIN rules from Scope §9, enforced on the backend.
- **Consistency:** accounts and the first card appear asynchronously after registration
  (eventual consistency); the UI shows an empty state until they exist.
- **Idempotency:** the `auth.user-registered.v1` consumer is idempotent (processed_event + unique constraint).
- **Events:** `cards.status-changed.v1` is published via the outbox in the same transaction as the status change.

## Edge cases & errors

| Case | Expected behaviour | HTTP status / event |
|---|---|---|
| Card belongs to another user | Same response as for a non-existent card | 404 |
| Card does not exist | Error | 404 |
| "Add card" before the user's accounts exist | No card created | 404 |
| "Add card" with a missing or unknown accountType | No card created; detail "accountType must be CHECKING or SAVINGS" | 400 |
| User has only blocked cards | Dashboard "My Cards" shows none of them; the Credit Cards page lists them | 200 |
| Card is already blocked | No change, no event | 409 |
| Change PIN on a blocked card | No change | 409 |
| Change PIN on an expired card | No change | 409 |
| PIN violates a rule | Error names the violated rule | 422 |
| New PIN equals the current PIN | No change | 422 |
| User already has 5 non-blocked cards | No card created | 409 |
| Duplicate `auth.user-registered.v1` | Ignored, nothing new created | — |
| Malformed `auth.user-registered.v1` | Sent to DLT, nothing created | auth.user-registered.v1.DLT |
| User opens Credit Cards before the event is processed | Empty state, cards appear after refresh | 200 with empty list |

## Open questions

1. Can a blocked card be unblocked?
   Options: (a) no, blocking is final; (b) yes, by the user; (c) only via support.
   Proposal: (a) for this ticket.
   **Answer (PO):** (a). Blocking is final; unblocking is out of scope.
2. The Figma "Add New Card" form has Card Number, Name on Card and Expiration Date fields.
   We generate these on the backend. Is this deviation from the design OK?
   **Answer (PO):** Yes. Only DEBIT cards in this ticket, so the form asks for the account (CHECKING or SAVINGS) and a PIN.
3. Should the user be able to delete a card?
   Proposal: no, only block it.
   **Answer (PO):** No, only block.
4. The date-of-birth PIN rule needs the birth date, which lives in profile-service, while account-service validates the PIN. How do we get it?
   Options: (a) drop the rule for now; (b) profile-service publishes the birth date in an event; (c) synchronous call to profile-service.
   **Answer (PO):** (a) for this ticket, replaced by the generic "year 1950–2029" rule. Revisit later.
5. Is there a limit on the number of cards per user?
   **Answer (PO):** Yes, at most 5 non-blocked cards per user.
6. The Add card form picks CHECKING or SAVINGS (Q2), but no endpoint returns the user's account ids, so the
   form cannot send an accountId for SAVINGS.
   Options: (a) add `GET /api/accounts`; (b) `POST /api/cards` takes `accountType` instead of `accountId`;
   (c) CHECKING only.
   **Answer:** (b). `POST /api/cards` takes `{ accountType, pin }`; account-service resolves the caller's
   account of that type. AC7 is removed, AC19 covers a missing account.
7. Where does the holder name of a card added via `POST /api/cards` come from?
   Options: (a) copy it from the registration card; (b) a `customer` table filled from
   `auth.user-registered.v1`; (c) a `holderName` field in the request.
   **Answer:** (b). account-service keeps `customer (user_id, full_name)`.
8. **CR-1 (change request from the PO, after the first verification):** (1) Which cards does the Dashboard
   "My Cards" widget show, and in what order does the Credit Cards page list cards? (2) What does an unknown
   or missing accountType on `POST /api/cards` return?
   **Answer (PO):** (1) The widget shows only non-blocked cards, newest first. The Credit Cards page lists
   non-blocked cards first (newest first), then blocked ones. (2) 400 ProblemDetail with detail
   "accountType must be CHECKING or SAVINGS". AC17 is updated, AC20 (order) and AC21 (account type) are added.
   **Assumptions (not stated by the PO):** blocked cards are also listed newest first; "non-blocked" is the stored
   status, as for the card limit (AC16), so an expired card counts as non-blocked; the order is applied by
   `GET /api/cards`, so `limit` returns the first N of it, and the widget drops blocked cards from its 2.
