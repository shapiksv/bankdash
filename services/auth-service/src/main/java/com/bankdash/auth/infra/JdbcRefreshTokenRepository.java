package com.bankdash.auth.infra;

import com.bankdash.auth.domain.RefreshToken;
import com.bankdash.auth.domain.RefreshTokenRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRefreshTokenRepository implements RefreshTokenRepository {

    private final JdbcClient jdbc;

    JdbcRefreshTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(RefreshToken token) {
        jdbc.sql("""
                        INSERT INTO refresh_token
                            (id, user_id, family_id, token_hash, created_at, expires_at, family_expires_at, replaced_by, revoked)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(token.id(), token.userId(), token.familyId(), token.tokenHash(),
                        Timestamp.from(token.createdAt()), Timestamp.from(token.expiresAt()),
                        Timestamp.from(token.familyExpiresAt()), token.replacedBy(), token.revoked())
                .update();
    }

    @Override
    public Optional<RefreshToken> findByHashForUpdate(String tokenHash) {
        return jdbc.sql("SELECT * FROM refresh_token WHERE token_hash = ? FOR UPDATE")
                .param(tokenHash)
                .query(JdbcRefreshTokenRepository::map)
                .optional();
    }

    @Override
    public void markReplaced(UUID id, UUID replacedBy) {
        jdbc.sql("UPDATE refresh_token SET replaced_by = ? WHERE id = ?").params(replacedBy, id).update();
    }

    @Override
    public void revokeFamily(UUID familyId) {
        jdbc.sql("UPDATE refresh_token SET revoked = true WHERE family_id = ?").param(familyId).update();
    }

    private static RefreshToken map(ResultSet rs, int row) throws SQLException {
        return new RefreshToken(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("family_id", UUID.class),
                rs.getString("token_hash"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("family_expires_at").toInstant(),
                rs.getObject("replaced_by", UUID.class),
                rs.getBoolean("revoked"));
    }
}
