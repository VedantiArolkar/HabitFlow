package com.habitflow.repository;

import com.habitflow.model.Notification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class NotificationRepository {

    private static final RowMapper<Notification> MAPPER = (rs, i) -> new Notification(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("type"),
            rs.getString("title"),
            rs.getString("message"),
            rs.getString("link"),
            rs.getBoolean("is_read"),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Long insert(Long userId, String type, String title, String message, String link) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO notifications (user_id, type, title, message, link) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, type);
            ps.setString(3, title);
            ps.setString(4, message);
            ps.setString(5, link);
            return ps;
        }, kh);
        Number key = kh.getKey();
        if (key == null) {
            throw new IllegalStateException("Notification insert did not return an id");
        }
        return key.longValue();
    }

    public List<Notification> findByUser(Long userId, boolean unreadOnly, int limit) {
        String sql = "SELECT id, user_id, type, title, message, link, is_read, created_at FROM notifications " +
                "WHERE user_id = ? " + (unreadOnly ? "AND is_read = 0 " : "") +
                "ORDER BY created_at DESC, id DESC LIMIT ?";
        return jdbc.query(sql, MAPPER, userId, limit);
    }

    public int countUnread(Long userId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0",
                Integer.class, userId);
        return c == null ? 0 : c;
    }

    public int markRead(Long id, Long userId) {
        return jdbc.update("UPDATE notifications SET is_read = 1 WHERE id = ? AND user_id = ?", id, userId);
    }

    public int markAllRead(Long userId) {
        return jdbc.update("UPDATE notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0", userId);
    }

    public int delete(Long id, Long userId) {
        return jdbc.update("DELETE FROM notifications WHERE id = ? AND user_id = ?", id, userId);
    }

    /** Guards against spamming the same notification type on the same day. */
    public boolean existsSince(Long userId, String type, LocalDateTime since) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = ? AND created_at >= ?",
                Integer.class, userId, type, java.sql.Timestamp.valueOf(since));
        return c != null && c > 0;
    }
}
