package com.bankdash.profile.infra;

import com.bankdash.profile.domain.Preferences;
import com.bankdash.profile.domain.Preferences.Currency;
import com.bankdash.profile.domain.PreferencesRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPreferencesRepository implements PreferencesRepository {

    private final JdbcClient jdbc;

    JdbcPreferencesRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertIfAbsent(Preferences p) {
        jdbc.sql("""
                        INSERT INTO preferences (user_id, currency, time_zone, notify_on_transfers,
                            notify_on_merchant_orders, notify_on_recommendations)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (user_id) DO NOTHING""")
                .params(p.userId(), p.currency().name(), p.timeZone(), p.notifyOnTransfers(),
                        p.notifyOnMerchantOrders(), p.notifyOnRecommendations())
                .update();
    }

    @Override
    public Optional<Preferences> findById(UUID userId) {
        return jdbc.sql("SELECT * FROM preferences WHERE user_id = ?")
                .param(userId)
                .query(JdbcPreferencesRepository::map)
                .optional();
    }

    @Override
    public void update(Preferences p) {
        jdbc.sql("""
                        UPDATE preferences
                        SET currency = ?, time_zone = ?, notify_on_transfers = ?, notify_on_merchant_orders = ?,
                            notify_on_recommendations = ?
                        WHERE user_id = ?""")
                .params(p.currency().name(), p.timeZone(), p.notifyOnTransfers(), p.notifyOnMerchantOrders(),
                        p.notifyOnRecommendations(), p.userId())
                .update();
    }

    private static Preferences map(ResultSet rs, int row) throws SQLException {
        return new Preferences(
                rs.getObject("user_id", UUID.class),
                Currency.valueOf(rs.getString("currency")),
                rs.getString("time_zone"),
                rs.getBoolean("notify_on_transfers"),
                rs.getBoolean("notify_on_merchant_orders"),
                rs.getBoolean("notify_on_recommendations"));
    }
}
