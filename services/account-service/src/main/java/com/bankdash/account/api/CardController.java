package com.bankdash.account.api;

import com.bankdash.account.app.CardService;
import com.bankdash.account.domain.Card;
import com.bankdash.account.domain.CardNumbers;
import com.bankdash.account.domain.CardStatus;
import com.bankdash.account.domain.CardType;
import com.bankdash.web.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cards")
class CardController {

    private final CardService cards;

    CardController(CardService cards) {
        this.cards = cards;
    }

    /** The Dashboard asks for {@code limit=2}; the Credit Cards page for all of them. */
    @GetMapping
    CardListResponse list(@CurrentUser UUID userId, @RequestParam @Min(1) @Max(100) @Nullable Integer limit) {
        YearMonth today = cards.today();
        return new CardListResponse(cards.list(userId, limit).stream().map(card -> CardResponse.of(card, today)).toList());
    }

    @GetMapping("/{id}")
    CardResponse get(@CurrentUser UUID userId, @PathVariable UUID id) {
        return CardResponse.of(cards.get(userId, id), cards.today());
    }

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
