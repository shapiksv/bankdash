package com.bankdash.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankdash.testsupport.ContainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(ContainersConfig.class)
class AuthServiceApplicationIT {

    @Autowired
    JdbcClient jdbc;

    @Test
    void flywayCreatesTheSchema() {
        var tables = jdbc.sql("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name IN ('user_credential', 'refresh_token')
                        ORDER BY table_name""")
                .query(String.class)
                .list();

        assertThat(tables).containsExactly("refresh_token", "user_credential");
    }
}
