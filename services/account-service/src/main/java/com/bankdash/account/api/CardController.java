package com.bankdash.account.api;

import com.bankdash.account.app.CardService;
import com.bankdash.account.domain.AccountType;
import com.bankdash.account.domain.Card;
import com.bankdash.account.domain.CardNumbers;
import com.bankdash.account.domain.CardStatus;
import com.bankdash.account.domain.CardType;
import com.bankdash.web.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cards")
class CardController {

    private final CardService cards;

    CardController(CardService cards) {
        this.cards = cards;
    }

    /**
     * Non-blocked cards newest first, then blocked ones. The Dashboard asks for {@code limit=2} (and leaves out
     * blocked cards); the Credit Cards page for all of them.
     */
    @GetMapping
    CardListResponse list(@CurrentUser UUID userId, @RequestParam @Min(1) @Max(100) @Nullable Integer limit) {
        YearMonth today = cards.today();
        return new CardListResponse(cards.list(userId, limit).stream().map(card -> CardResponse.of(card, today)).toList());
    }

    @GetMapping("/{id}")
    CardResponse get(@CurrentUser UUID userId, @PathVariable UUID id) {
        return CardResponse.of(cards.get(userId, id), cards.today());
    }

    /**
     * 201 with the new card: ACTIVE, since it gets its PIN right away. The account type is parsed here rather than
     * bound as an enum, so a missing or unknown one gets its own 400 detail instead of "Failed to read request".
     */
    @PostMapping
    ResponseEntity<CardResponse> add(@CurrentUser UUID userId, @Valid @RequestBody AddCardRequest request) {
        AccountType accountType = AccountType.parse(request.accountType()).orElseThrow(InvalidAccountTypeException::new);
        Card card = cards.add(userId, accountType, request.pin());
        return ResponseEntity.created(URI.create("/cards/" + card.id())).body(CardResponse.of(card, cards.today()));
    }

    /** 204; the PIN rules are checked by the service (422), so the request only needs a value. */
    @PutMapping("/{id}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setPin(@CurrentUser UUID userId, @PathVariable UUID id, @Valid @RequestBody SetPinRequest request) {
        cards.setPin(userId, id, request.pin());
    }

    /** Final: there is no unblocking. The web app asks for confirmation first. */
    @PostMapping("/{id}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void block(@CurrentUser UUID userId, @PathVariable UUID id) {
        cards.block(userId, id);
    }

    record SetPinRequest(@NotNull String pin) {}

    record AddCardRequest(@Nullable String accountType, @NotNull String pin) {}

    record CardListResponse(List<CardResponse> cards) {}

    /** Never the full number or anything about the PIN. */
    record CardResponse(
            UUID id, UUID accountId, CardType type, String maskedNumber, String holderName, String expiry,
            CardStatus status) {

        static CardResponse of(Card card, YearMonth today) {
            return new CardResponse(card.id(), card.accountId(), card.type(), CardNumbers.mask(card.last4()),
                    card.holderName(), card.expiryText(), card.displayStatus(today));
        }
    }
}
