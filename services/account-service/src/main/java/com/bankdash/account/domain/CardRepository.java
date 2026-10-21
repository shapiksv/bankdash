package com.bankdash.account.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository {

    void insert(Card card);

    /** The user's cards, oldest first, at most {@code limit}. */
    List<Card> findByUser(UUID userId, int limit);

    /** Empty if the card does not exist or belongs to another user. */
    Optional<Card> findOwned(UUID cardId, UUID userId);
}
