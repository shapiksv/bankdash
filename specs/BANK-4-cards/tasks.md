# BANK-4: Cards and accounts for new customers — Tasks

Each task is roughly one commit that compiles and passes `./gradlew test` (and `npm test` for web tasks) on its
own. See [plan.md](plan.md) for the design (D1–D15) and [spec.md](spec.md) for the ACs.

- [x] **T1** — Add the `cards.status-changed.v1` event contract and topics
  - Files: `libs/events/src/main/java/com/bankdash/events/CardStatusChanged.java`, `libs/events/src/main/java/com/bankdash/events/Topics.java`, `libs/events/src/test/java/com/bankdash/events/EventJsonTest.java`, `infra/kafka/topics.txt`
  - Covers: AC13 (event contract; publishing comes in T8)
  - Tests: `EventJsonTest.cardStatusChangedHasTheDocumentedShape`, `EventJsonTest.dltTopicNames` (extended)

- [x] **T2** — Scaffold account-service with its schema
  - Files: `services/account-service/build.gradle.kts`, `src/main/resources/application.yml`, `src/main/resources/db/migration/V1__accounts_and_cards.sql`, `src/main/java/com/bankdash/account/AccountServiceApplication.java`, `infra/ClockConfig.java`, `infra/PinHashingConfig.java`, test `AccountIT.java` (base class: containers, TestJwtIssuer, `register(...)` helper), `AccountServiceApplicationIT.java`
  - Covers: — (infrastructure: the service, its database schema with `customer`/`account`/`card`/`outbox_event`/`processed_event`, and the test base every later backend task needs)
  - Tests: `AccountServiceApplicationIT.contextLoadsAndMigrationsApply`

- [x] **T3** — Implement the PIN policy
  - Files: `domain/PinPolicy.java`, `domain/PinRule.java`, `domain/WeakPinException.java`, test `domain/PinPolicyTest.java`
  - Covers: AC10, AC11 (the `SAME_AS_CURRENT` rule code; it is enforced in T7)
  - Tests: `PinPolicyTest.ac10_rejectsWeakPin` (parameterized over every rule and the spec's examples, asserting the rule), `PinPolicyTest.ac10_acceptsStrongPin`, `PinPolicyTest.ac10_rulesAreCheckedInSpecOrder`

- [x] **T4** — Add the card domain: number generation, masking, expiry and status
  - Files: `domain/CardNumbers.java`, `domain/Card.java`, `domain/CardStatus.java`, `domain/CardType.java`, `domain/AccountType.java`, `domain/Account.java`, `domain/Customer.java`, tests `domain/CardNumbersTest.java`, `domain/CardTest.java`
  - Covers: AC1 (expiry +3 years), AC3 (masking), AC4 (displayed status), AC12 (expiry derivation)
  - Tests: `CardNumbersTest.ac1_generatesSixteenLuhnValidDigits`, `CardNumbersTest.ac3_masksAllButLastFour`, `CardTest.ac1_expiresThreeYearsAfterIssue`, `CardTest.ac4_reportsExpiredAfterTheExpiryMonth`, `CardTest.ac4_blockedWinsOverExpired`, `CardTest.ac12_isValidThroughTheLastDayOfTheExpiryMonth`

- [x] **T5** — Open accounts and a card from `auth.user-registered.v1`
  - Files: `domain/CustomerRepository.java`, `domain/AccountRepository.java`, `domain/CardRepository.java`, `infra/JdbcCustomerRepository.java`, `infra/JdbcAccountRepository.java`, `infra/JdbcCardRepository.java`, `app/AccountService.java`, `app/CardIssuer.java` (shared with T9), `infra/UserRegisteredListener.java`, test `infra/UserRegisteredListenerIT.java`
  - Covers: AC1, AC2
  - Tests: `ac1_opensCheckingSavingsAndAnInactiveDebitCard`, `ac1_failureRollsBackAndTheRetryCreatesEverything`, `ac2_duplicateEventIsIgnored`, `ac2_secondEventForTheSameUserCreatesNothing`, `poisonMessageLandsInTheDlt`, `eventWithoutUserIdLandsInTheDlt`

- [ ] **T6** — List cards and show card details
  - Files: `app/CardService.java` (list, get), `domain/CardNotFoundException.java`, `api/CardController.java` (GET endpoints, `CardResponse`, `CardListResponse`), `api/CardExceptionHandler.java`, test `api/CardReadApiIT.java`
  - Covers: AC3, AC4, AC5, AC18
  - Tests: `ac3_listsOnlyOwnCardsWithMaskedNumbers`, `ac3_limitReturnsTheFirstCardsInCreationOrder`, `ac3_noCardsYetIsAnEmptyList`, `ac4_returnsCardDetails`, `ac4_reportsExpiredCardAsExpired`, `ac5_someoneElsesCardIs404LikeAMissingOne`, `ac18_requestWithoutTokenIs401ProblemDetail`

- [ ] **T7** — Set and change the PIN
  - Files: `app/CardService.java` (setPin), `domain/CardNotUsableException.java`, `api/CardController.java` (PUT `/cards/{id}/pin`, `SetPinRequest`), `api/CardExceptionHandler.java` (422 with `rule`, 409), test `api/CardPinApiIT.java`
  - Covers: AC5, AC8, AC9, AC10, AC11, AC12
  - Tests: `ac8_firstPinActivatesTheCardAndIsStoredHashed`, `ac9_changesThePin`, `ac10_weakPinIs422WithTheRuleAndPinUnchanged` (parameterized: 0000, 1234, 9876, 1212, 1998, 2580, abcd, 123), `ac11_samePinAsCurrentIs422`, `ac12_blockedCardIs409`, `ac12_expiredCardIs409`, `ac5_someoneElsesCardIs404`

- [ ] **T8** — Block a card and publish `cards.status-changed.v1`
  - Files: `app/CardService.java` (block, `Outbox.publish`), `domain/CardAlreadyBlockedException.java`, `api/CardController.java` (POST `/cards/{id}/block`), `api/CardExceptionHandler.java`, test `api/CardBlockApiIT.java`
  - Covers: AC5, AC13, AC14
  - Tests: `ac13_blocksTheCardAndPublishesStatusChanged`, `ac13_blocksAnInactiveCard`, `ac14_alreadyBlockedIs409AndPublishesNothing`, `ac5_someoneElsesCardIs404AndStaysUnblocked`

- [ ] **T9** — Add a card to the caller's account of a given type
  - Files: `app/CardService.java` (add), `domain/AccountNotFoundException.java`, `domain/CardLimitReachedException.java`, repositories (`lockCustomer`, `countNonBlocked`, `findByUserAndType`), `api/CardController.java` (POST `/cards`, `AddCardRequest`), `api/CardExceptionHandler.java`, test `api/AddCardApiIT.java`
  - Covers: AC6, AC10, AC16, AC19
  - Tests: `ac6_addsAnActiveCardToTheCheckingAccount`, `ac6_addsAnActiveCardToTheSavingsAccount`, `ac6_holderNameComesFromTheRegisteredCustomer`, `ac10_weakPinOnAddIs422AndNoCardIsCreated`, `ac16_sixthNonBlockedCardIs409`, `ac16_blockedCardsDoNotCountTowardsTheLimit`, `ac16_concurrentAddsNeverExceedTheLimit`, `ac19_noAccountsYetIs404`, `unknownAccountTypeIs400`

- [ ] **T10** — Route `/api/cards` through the gateway
  - Files: `services/api-gateway/src/main/resources/application.yml`, `services/api-gateway/src/test/java/com/bankdash/gateway/GatewayIT.java`
  - Covers: AC18
  - Tests: `GatewayIT.cardRequestsGoToAccountService`, `GatewayIT.cardCommandsKeepMethodAndPath`, `GatewayIT.ac18_cardsWithoutTokenIs401`, `GatewayIT.ac18_cardsWithInvalidTokenIs401`

- [ ] **T11** — Add the web cards API, query hooks and PIN rules
  - Files: `apps/web/src/api/cards.ts`, `apps/web/src/api/cards.test.ts`, `apps/web/src/cards/queries.ts`, `apps/web/src/cards/pinRules.ts`, `apps/web/src/cards/pinRules.test.ts`, `apps/web/src/test/renderWithAuth.tsx` (default `GET /api/cards`), `apps/web/src/test/users.ts` (card fixtures)
  - Covers: AC6 (request shape), AC10 (UI mirror of the rules)
  - Tests: `it('AC6: adds a card with accountType and pin')`, `it('AC10: flags <pin> as <rule>')` (same examples as `PinPolicyTest`), `it('AC10: accepts strong PINs')`

- [ ] **T12** — Build the Credit Cards page: list, details and empty state
  - Files: `apps/web/src/pages/CreditCardsPage.tsx`, `apps/web/src/cards/CardTile.tsx`, `apps/web/src/pages/CreditCardsPage.test.tsx`
  - Covers: AC4, AC17
  - Tests: `it('AC17: lists all cards with masked numbers')`, `it('AC17: shows an empty state while cards are being prepared')`, `it('AC4: shows status, expiry and linked account in the details')`

- [ ] **T13** — Block a card through a confirmation dialog
  - Files: `apps/web/src/cards/ConfirmDialog.tsx`, `apps/web/src/pages/CreditCardsPage.tsx`, `apps/web/src/pages/CreditCardsPage.test.tsx`
  - Covers: AC15, AC13 (UI)
  - Tests: `it('AC15: sends the block request only after Confirm')`, `it('AC15: Cancel sends no request')`, `it('AC13: shows the card as blocked and a confirmation message')`

- [ ] **T14** — Add the Set/Change PIN and Add card dialogs
  - Files: `apps/web/src/cards/PinDialog.tsx`, `apps/web/src/cards/AddCardDialog.tsx`, `apps/web/src/pages/CreditCardsPage.tsx`, `apps/web/src/pages/CreditCardsPage.test.tsx`
  - Covers: AC6, AC8, AC9, AC10
  - Tests: `it('AC8: offers Set PIN for an inactive card and shows it active after saving')`, `it('AC9: changes the PIN of an active card')`, `it('AC10: blocks a weak PIN before sending')`, `it('AC10: shows the backend rule detail on 422')`, `it('AC6: adds a card to the chosen account')`

- [ ] **T15** — Show the Dashboard "My Cards" widget
  - Files: `apps/web/src/cards/MyCardsWidget.tsx`, `apps/web/src/pages/DashboardPage.tsx`, `apps/web/src/pages/DashboardPage.test.tsx`
  - Covers: AC17
  - Tests: `it('AC17: shows at most 2 cards with masked numbers')` (asserts `limit=2` in the request)

- [ ] **T16** — Document account-service in CLAUDE.md
  - Files: `CLAUDE.md`
  - Covers: — (keeps the commands and repository structure accurate now that a third service exists)
  - Tests: none (docs only); `./gradlew build` and `npm test` stay green

## AC → tasks coverage

| AC | Tasks |
|---|---|
| AC1 | T4, T5 |
| AC2 | T5 |
| AC3 | T4, T6 |
| AC4 | T4, T6, T12 |
| AC5 | T6, T7, T8 |
| AC6 | T9, T11, T14 |
| AC7 | — Removed in the spec (Q6) |
| AC8 | T7, T14 |
| AC9 | T7, T14 |
| AC10 | T3, T7, T9, T11, T14 |
| AC11 | T3, T7 |
| AC12 | T4, T7 |
| AC13 | T1, T8, T13 |
| AC14 | T8 |
| AC15 | T13 |
| AC16 | T9 |
| AC17 | T12, T15 |
| AC18 | T6, T10 |
| AC19 | T9 |

Every active AC (AC1–AC6, AC8–AC19) is covered by at least one task. AC7 is marked Removed in the spec.
