package com.bankdash.account.infra;

import com.bankdash.account.domain.Account;
import com.bankdash.account.domain.AccountRepository;
import java.sql.Timestamp;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAccountRepository implements AccountRepository {

    private final JdbcClient jdbc;

    JdbcAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Account a) {
        jdbc.sql("""
                        INSERT INTO account (id, user_id, type, currency, balance, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)""")
                .params(a.id(), a.userId(), a.type().name(), a.currency(), a.balance(), Timestamp.from(a.createdAt()))
                .update();
    }
}
