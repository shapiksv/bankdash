# BANK-4: Cards and accounts for new customers

- **Status:** Draft 
- **Services:** <services/account-service, apps/web>

## Context

Logic needs to be added so that two cards are automatically created upon successful registration. Also, a Credit Cards page should be added, allowing for the editing of the card data щor create card.

## Scope

1. Immediately upon registration, two accounts—a savings account and a debit card—should be created for the user, with a zero balance.
- **card should include**
  - **card number** (16 digits)
  - **expiration date** (MM/YY)
  - **PIN** (4 digits. empty by default and inactive)
  - **status** (inactive, active, blocked, expired)
  - **user name** (from registration)
2. After registration, these cards should appear on the credit cards page.
3. Users see only their own cards.
4. The 8 central digits of the card number are hidden.
5. The user must have the ability to:
- **open card details**
  - if the card inactive and PIN is empty, the user should be prompted to set a PIN.
- **add a new card**
- **block a card**
  - A confirmation step should be added when blocking to ensure the user is certain, in order to prevent accidental blocking.
- **change the PIN**
  - The new PIN must not violate the following rules:
    - Simple sequences: Avoid combinations such as 1234, 4321, 0000, 9999, etc.
    - Obvious dates: Do not use your birth year (e.g., 1998) or your birth day and month (e.g., 1204 for April 12). Fraudsters can easily obtain this information from your social media profiles or passport.
    - Mirrored or repeating pairs: Combinations such as 1212, 6969, or 2020.
    - Visual lines on the keypad: Do not choose digits that appear in a straight line—vertically or diagonally—on the ATM keypad (e.g., 2580 or 1593). These are very easy for someone to spot by looking over your shoulder.
    - The new PIN must not match the old PIN.
    - It is not permitted to change a card that has an expiration date in the past.

## Out of scope

Cards number generation and validation, as well as card expiration date generation and validation, are out of scope for this ticket. These are handled by the card service.


## User stories

- As a user, I have the option to add a card by clicking the "Add card" button.
- As a user, I have the option to block my card; the system should ask me twice if I am sure.
- As a user, I have the option to change my PIN. The system must validate the request; if successful, it should notify me, and if not, it should inform me what went wrong.
- As a user, I can view my cards on the credit cards page, but I cannot see the 8 central digits of the card number.
- As a user, I can view the details of my card by clicking on it.

## Acceptance criteria

- **AC1** — Cards and accounts for new customers automatically created
  - **Given** After registration, the user service must send an event to the account service to create an account.
    - The account service must create a savings account and a debit card for the user, with a zero balance.
    - Event include user name and UserId
    - Each card should have a unique card number, expiration date, and status set to inactive. field PIN should be empty.
    - account-service generates card data: a unique 16-digit card number and an expiry date
      (e.g. 3 years from creation). Generated numbers are fake (learning project, no real payment network).
  - **When** If cards have been created, the cards page should display them.
  - **Then** On successful registration, account-service creates for the user:
    - a CHECKING account with zero balance,
    - a SAVINGS account with zero balance, 
    - one DEBIT card linked to the CHECKING account, status INACTIVE, no PIN..
- **AC2** — Create new card
  - **Given** The user is on the credit cards page and clicks the "Add card" button.
  - **When** The user fills in the required(PIN) information and submits the form.
    - frontend should validate the PIN according to the rules specified in the scope section.
    - frontend should return accountId and PIN to the backend to create a new card. accountId should check if it belongs to the userId from the token.
    - backend should create a new card with the provided userId (from token) and PIN and add all necessary details.
  - **Given** The user is on the credit cards page and clicks the "Add card" button.
  - **When** The user not owns the accountId provided in the request, 
  - **Then** the backend should return a 400 bad request error.
- **AC3** — Block card
  - **Given** The user is on the credit cards page and clicks the "Block card" button for a specific card.
  - **When** The user confirms the action in the confirmation dialog.
  - **Then** The card's status should be updated to "blocked," and it should no longer be usable for transactions. The user should see a notification confirming the card has been blocked.
  - **Given** The user is on the credit cards page and clicks the "Block card" button for a specific card.
  - **When** The user not owns the cardId provided in the request,
  - **Then** the backend should return a 404 not found error.
- **AC4** — Change PIN
  - **Given** The user is on the credit cards page and clicks the "Change PIN" button for a specific card.
  - **When** The user enters a new PIN and submits the form.
  - **Then** The card's PIN should be updated, and the user should see a notification confirming the PIN has been changed.
  - **Given** The user is on the credit cards page and clicks the "Change PIN" button for a specific card.
  - **When** The user set invalid PIN according to the rules specified in the scope section.
  - **Then** the backend should return a 400 bad request error, with a message indicating the reason for the failure.
  - **Given** The user is on the credit cards page and clicks the "Change PIN" button for a specific card.
  - **When** The user not owns the cardId provided in the request,
  - **Then** the backend should return a 404 not found error.

## API contract

Paths as seen through the gateway (`/api/...`).

**Get cards**: Retrieves a list of cards associated with the authenticated user.

| Method | Path   | Auth  | Request | Response | Errors (ProblemDetail) |
|GET     | /cards | token |         |---|---|

Request:
Response examples:

```
{ 
    "cards": [
        {
        "cardId": "1",
        "cardNumber": "1234 **** **** 5678",
        "expirationDate": "12/25",
        "status": "inactive",
        "userName": "John Doe"
        },
        {
        "cardId": "2",
        "cardNumber": "9876 **** **** 5432",
        "expirationDate": "11/24",
        "status": "active",
        "userName": "John Doe"
        }
    ]
}

```

** Block card**: Blocks a specific card associated with the authenticated user.
| Method | Path               | Auth  | Request | Response | Errors (ProblemDetail) |
|POST    | /cards/{id}/block | token |  id     | 201      | 404 not found |

Request:
Response examples:

**Change PIN**: Changes the PIN for a specific card associated with the authenticated user.

| Method | Path            | Auth  | Request | Response | Errors (ProblemDetail) |
|POST    | /cards/{id}/pin | token |         | 201      | 404 not found, 400 bad request     |

Request:

```
{ 
    "PIN": "1234"
}

```
**Create card**: Creates a new card for the authenticated user.

| Method | Path   | Auth  | Request | Response | Errors (ProblemDetail) |
|POST    | /cards | token |         | 204      | 400 bad request        |

Request:

```
{ 
    "PIN": "1234",
    "accountId": "1"
}

```

## Events

** Create-card**: Event emitted when a new card is created for a user.`
| Topic (`.v1`)              | Producer (via outbox) | Consumer(s) (idempotent) | Payload | DLT |
|auth.user-registered.v1|true                   |account-service           |         |---|

Payload
```
{ 
    "type": "CREATE_CARD",
    "userName": "John Doe"
}

```

## Data model

Card table structure:
Id, cardNumber, expirationDate, pinHash(BCrypt), status, userName, userId, createdAt, updatedAt, accountId

## Non-functional

Security team: a customer must never see someone else’s cards, and full card numbers must never leave the backend.
Support team: customers often block a card by mistake and then call us. Make blocking hard to do by accident.

## Edge cases & errors

| Case                       | Expected behaviour                                              | HTTP status / event |
|Card belongs to another user|Same response as for a non-existent card (don’t reveal it exists)| 404                 |
|Card is already blocked     |No change, error explains it                                     | 409                 |
|Card does not exist         |Error                                                            | 404                 |
|PIN is invalid              |Error with explanation                                           | 404                 |
|update PIN in blocked card  |Error with explanation                                           | 404                 |


## Open questions

1. Can a blocked card be unblocked?
   Options: (a) no, blocking is final; (b) yes, by the user; (c) only via support.
   Proposal: (a) for this ticket.
2. The Figma "Add New Card" form has Card Number, Name on Card and Expiration Date fields.
   We generate these on the backend, so the user only enters a PIN. Is this deviation from the design OK?
   Proposal: yes; the form asks only for card type and PIN.
3. Should the user be able to delete a card?
   Proposal: no, only block it.
