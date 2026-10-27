package com.bankdash.account.infra;

import com.bankdash.account.domain.Account;
import com.bankdash.account.domain.AccountRepository;
import com.bankdash.account.domain.AccountType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    @Override
    public Optional<Account> findByUserAndType(UUID userId, AccountType type) {
        return jdbc.sql("SELECT * FROM account WHERE user_id = ? AND type = ?")
                .params(userId, type.name())
                .query(JdbcAccountRepository::map)
                .optional();
    }

    @Override
    public List<Account> findByUser(UUID userId) {
        return jdbc.sql("SELECT * FROM account WHERE user_id = ?")
                .param(userId)
                .query(JdbcAccountRepository::map)
                .list();
    }

    private static Account map(ResultSet rs, int row) throws SQLException {
        return new Account(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                AccountType.valueOf(rs.getString("type")),
                rs.getString("currency"),
                rs.getBigDecimal("balance"),
                rs.getTimestamp("created_at").toInstant());
    }
}
