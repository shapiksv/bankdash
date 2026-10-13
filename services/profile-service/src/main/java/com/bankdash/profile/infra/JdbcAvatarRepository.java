package com.bankdash.profile.infra;

import com.bankdash.profile.domain.Avatar;
import com.bankdash.profile.domain.AvatarRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAvatarRepository implements AvatarRepository {

    private final JdbcClient jdbc;

    JdbcAvatarRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Avatar avatar) {
        jdbc.sql("""
                        INSERT INTO avatar (user_id, content_type, data, updated_at) VALUES (?, ?, ?, ?)
                        ON CONFLICT (user_id) DO UPDATE
                        SET content_type = excluded.content_type, data = excluded.data, updated_at = excluded.updated_at""")
                .params(avatar.userId(), avatar.contentType(), avatar.data(), Timestamp.from(avatar.updatedAt()))
                .update();
    }

    @Override
    public Optional<Avatar> findById(UUID userId) {
        return jdbc.sql("SELECT * FROM avatar WHERE user_id = ?")
                .param(userId)
                .query((rs, row) -> new Avatar(rs.getObject("user_id", UUID.class), rs.getString("content_type"),
                        rs.getBytes("data"), rs.getTimestamp("updated_at").toInstant()))
                .optional();
    }

    @Override
    public Optional<Instant> updatedAt(UUID userId) {
        return jdbc.sql("SELECT updated_at FROM avatar WHERE user_id = ?")
                .param(userId)
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant);
    }
}
