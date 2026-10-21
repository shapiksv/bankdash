package com.bankdash.account.infra;

import com.bankdash.account.domain.Customer;
import com.bankdash.account.domain.CustomerRepository;
import java.sql.Timestamp;
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
}
