package com.habitflow.repository;

import com.habitflow.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private static final RowMapper<User> MAPPER = (rs, i) -> new User(
            rs.getLong("id"),
            rs.getString("full_name"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("role"),
            rs.getString("timezone"),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findByEmail(String email) {
        List<User> rows = jdbc.query(
                "SELECT id, full_name, email, password_hash, role, timezone, created_at " +
                        "FROM users WHERE email = ?", MAPPER, email);
        return rows.stream().findFirst();
    }

    public Optional<User> findById(Long id) {
        List<User> rows = jdbc.query(
                "SELECT id, full_name, email, password_hash, role, timezone, created_at " +
                        "FROM users WHERE id = ?", MAPPER, id);
        return rows.stream().findFirst();
    }

    public boolean existsByEmail(String email) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        return count != null && count > 0;
    }

    public Long insert(String fullName, String email, String passwordHash, String timezone) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO users (full_name, email, password_hash, role, timezone) " +
                            "VALUES (?, ?, ?, 'ROLE_USER', ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.setString(4, timezone == null ? "UTC" : timezone);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("User insert did not return a generated id");
        }
        return key.longValue();
    }

    public int updateProfile(Long userId, String fullName, String email, String timezone) {
        return jdbc.update(
                "UPDATE users SET full_name = ?, email = ?, timezone = ? WHERE id = ?",
                fullName, email, timezone == null ? "UTC" : timezone, userId);
    }

    public int updatePassword(Long userId, String passwordHash) {
        return jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", passwordHash, userId);
    }
}
