package com.bankdash.account.domain;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    void insert(Account account);

    Optional<Account> findByUserAndType(UUID userId, AccountType type);
}
