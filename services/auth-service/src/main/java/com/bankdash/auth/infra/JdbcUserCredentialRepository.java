package com.bankdash.auth.infra;

import com.bankdash.auth.domain.EmailAlreadyRegisteredException;
import com.bankdash.auth.domain.UserCredential;
import com.bankdash.auth.domain.UserCredentialRepository;
import com.bankdash.auth.domain.UserStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserCredentialRepository implements UserCredentialRepository {

    private final JdbcClient jdbc;

    JdbcUserCredentialRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(UserCredential user) {
        try {
            jdbc.sql("""
                            INSERT INTO user_credential (id, email, password_hash, two_factor_enabled, status, created_at)
                            VALUES (?, ?, ?, ?, ?, ?)""")
                    .params(user.id(), user.email(), user.passwordHash(), user.twoFactorEnabled(),
                            user.status().name(), Timestamp.from(user.createdAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            throw new EmailAlreadyRegisteredException(user.email());
        }
    }

    @Override
    public Optional<UserCredential> findByEmail(String normalizedEmail) {
        return jdbc.sql("SELECT * FROM user_credential WHERE email = ?")
                .param(normalizedEmail)
                .query(JdbcUserCredentialRepository::map)
                .optional();
    }

    @Override
    public Optional<UserCredential> findById(UUID id) {
        return jdbc.sql("SELECT * FROM user_credential WHERE id = ?")
                .param(id)
                .query(JdbcUserCredentialRepository::map)
                .optional();
    }

    @Override
    public void updatePasswordHash(UUID id, String passwordHash) {
        jdbc.sql("UPDATE user_credential SET password_hash = ? WHERE id = ?").params(passwordHash, id).update();
    }

    @Override
    public void updateTwoFactorEnabled(UUID id, boolean enabled) {
        jdbc.sql("UPDATE user_credential SET two_factor_enabled = ? WHERE id = ?").params(enabled, id).update();
    }

    private static UserCredential map(ResultSet rs, int row) throws SQLException {
        return new UserCredential(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getBoolean("two_factor_enabled"),
                UserStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant());
    }
}
