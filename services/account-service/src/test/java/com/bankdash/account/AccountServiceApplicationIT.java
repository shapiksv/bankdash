package com.bankdash.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountServiceApplicationIT extends AccountIT {

    @Test
    void contextLoadsAndMigrationsApply() {
        var tables = jdbc.sql("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN ('customer', 'account', 'card', 'outbox_event', 'processed_event')
                        ORDER BY table_name""")
                .query(String.class)
                .list();

        assertThat(tables).containsExactly("account", "card", "customer", "outbox_event", "processed_event");
    }
}
