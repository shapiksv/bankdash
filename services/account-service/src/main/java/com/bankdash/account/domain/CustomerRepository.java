package com.bankdash.account.domain;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {

    /** @return false if the customer already existed, which leaves it unchanged */
    boolean insertIfAbsent(Customer customer);

    /** Locks the customer until the transaction ends, which serializes that user's card creations. */
    Optional<Customer> lockById(UUID userId);
}
