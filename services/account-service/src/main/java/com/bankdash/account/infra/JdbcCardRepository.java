package com.bankdash.account.infra;

import com.bankdash.account.domain.Card;
import com.bankdash.account.domain.CardRepository;
import com.bankdash.account.domain.CardStatus;
import com.bankdash.account.domain.CardType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCardRepository implements CardRepository {

    private final JdbcClient jdbc;

    JdbcCardRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Card c) {
        jdbc.sql("""
                        INSERT INTO card (id, user_id, account_id, type, holder_name, last4, expiry_month, expiry_year,
                            pin_hash, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(c.id(), c.userId(), c.accountId(), c.type().name(), c.holderName(), c.last4(),
                        c.expiry().getMonthValue(), c.expiry().getYear(), c.pinHash(), c.status().name(),
                        Timestamp.from(c.createdAt()), Timestamp.from(c.updatedAt()))
                .update();
    }

    @Override
    public List<Card> findByUser(UUID userId, int limit) {
        return jdbc.sql("SELECT * FROM card WHERE user_id = ? ORDER BY created_at, id LIMIT ?")
                .params(userId, limit)
                .query(JdbcCardRepository::map)
                .list();
    }

    @Override
    public Optional<Card> findOwned(UUID cardId, UUID userId) {
        return jdbc.sql("SELECT * FROM card WHERE id = ? AND user_id = ?")
                .params(cardId, userId)
                .query(JdbcCardRepository::map)
                .optional();
    }

    static Card map(ResultSet rs, int row) throws SQLException {
        return new Card(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("account_id", UUID.class),
                CardType.valueOf(rs.getString("type")),
                rs.getString("holder_name"),
                rs.getString("last4"),
                YearMonth.of(rs.getInt("expiry_year"), rs.getInt("expiry_month")),
                rs.getString("pin_hash"),
                CardStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
