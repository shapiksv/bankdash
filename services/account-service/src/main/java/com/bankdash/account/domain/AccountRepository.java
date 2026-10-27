package com.bankdash.account.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    void insert(Account account);

    Optional<Account> findByUserAndType(UUID userId, AccountType type);

    /** All of the user's accounts, in one query. */
    List<Account> findByUser(UUID userId);
}
