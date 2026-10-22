package com.bankdash.account.infra;

import com.bankdash.account.domain.Customer;
import com.bankdash.account.domain.CustomerRepository;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCustomerRepository implements CustomerRepository {

    private final JdbcClient jdbc;

    JdbcCustomerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(Customer c) {
        return jdbc.sql("""
                        INSERT INTO customer (user_id, full_name, created_at) VALUES (?, ?, ?)
                        ON CONFLICT (user_id) DO NOTHING""")
                .params(c.userId(), c.fullName(), Timestamp.from(c.createdAt()))
                .update() == 1;
    }

    @Override
    public Optional<Customer> lockById(UUID userId) {
        return jdbc.sql("SELECT * FROM customer WHERE user_id = ? FOR UPDATE")
                .param(userId)
                .query((rs, row) -> new Customer(rs.getObject("user_id", UUID.class), rs.getString("full_name"),
                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }
}
