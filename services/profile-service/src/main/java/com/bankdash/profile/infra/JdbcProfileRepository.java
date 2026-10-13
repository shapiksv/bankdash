package com.bankdash.profile.infra;

import com.bankdash.profile.domain.ProfileRepository;
import com.bankdash.profile.domain.UserProfile;
import com.bankdash.profile.domain.UsernameTakenException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProfileRepository implements ProfileRepository {

    private final JdbcClient jdbc;

    JdbcProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(UserProfile p) {
        return jdbc.sql("""
                        INSERT INTO user_profile (user_id, full_name, username, date_of_birth, present_address,
                            permanent_address, city, postal_code, country, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (user_id) DO NOTHING""")
                .params(p.userId(), p.fullName(), p.username(), p.dateOfBirth(), p.presentAddress(),
                        p.permanentAddress(), p.city(), p.postalCode(), p.country(),
                        Timestamp.from(p.createdAt()), Timestamp.from(p.updatedAt()))
                .update() == 1;
    }

    @Override
    public boolean usernameTaken(String username) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM user_profile WHERE username = ?)")
                .param(username)
                .query(Boolean.class)
                .single();
    }

    @Override
    public Optional<UserProfile> findById(UUID userId) {
        return jdbc.sql("SELECT * FROM user_profile WHERE user_id = ?")
                .param(userId)
                .query(JdbcProfileRepository::map)
                .optional();
    }

    @Override
    public void update(UserProfile p) {
        try {
            jdbc.sql("""
                            UPDATE user_profile
                            SET full_name = ?, username = ?, date_of_birth = ?, present_address = ?,
                                permanent_address = ?, city = ?, postal_code = ?, country = ?, updated_at = ?
                            WHERE user_id = ?""")
                    .params(p.fullName(), p.username(), p.dateOfBirth(), p.presentAddress(), p.permanentAddress(),
                            p.city(), p.postalCode(), p.country(), Timestamp.from(p.updatedAt()), p.userId())
                    .update();
        } catch (DuplicateKeyException e) {
            throw new UsernameTakenException(p.username());
        }
    }

    private static UserProfile map(ResultSet rs, int row) throws SQLException {
        return new UserProfile(
                rs.getObject("user_id", UUID.class),
                rs.getString("full_name"),
                rs.getString("username"),
                rs.getObject("date_of_birth", LocalDate.class),
                rs.getString("present_address"),
                rs.getString("permanent_address"),
                rs.getString("city"),
                rs.getString("postal_code"),
                rs.getString("country"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
