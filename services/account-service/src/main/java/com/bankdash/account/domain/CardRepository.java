package com.bankdash.account.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository {

    void insert(Card card);

    /** The user's cards, at most {@code limit}: non-blocked ones newest first, then blocked ones newest first. */
    List<Card> findByUser(UUID userId, int limit);

    /** Empty if the card does not exist or belongs to another user. */
    Optional<Card> findOwned(UUID cardId, UUID userId);

    /** Like {@link #findOwned}, and locks the card until the transaction ends. */
    Optional<Card> lockOwned(UUID cardId, UUID userId);

    /** Saves the PIN hash, status and updatedAt of an existing card. */
    void update(Card card);

    /** Cards that count towards the card limit: every card that is not blocked, expired ones included. */
    int countNonBlocked(UUID userId);
}
