package com.habitflow.repository;

import com.habitflow.model.Goal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class GoalRepository {

    private static final String SELECT_BASE =
            "SELECT id, user_id, title, description, target_value, manual_progress, milestone_reached, " +
            "target_date, status, created_at, completed_at FROM goals ";

    private static final RowMapper<Goal> MAPPER = (rs, i) -> new Goal(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("title"),
            rs.getString("description"),
            (Integer) rs.getObject("target_value"),
            rs.getInt("manual_progress"),
            rs.getDate("target_date") == null ? null : rs.getDate("target_date").toLocalDate(),
            rs.getString("status"),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public GoalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Goal> findByUser(Long userId) {
        return jdbc.query(SELECT_BASE + "WHERE user_id = ? ORDER BY status = 'COMPLETED', " +
                "target_date IS NULL, target_date, id DESC", MAPPER, userId);
    }

    public Optional<Goal> findByIdAndUser(Long id, Long userId) {
        return jdbc.query(SELECT_BASE + "WHERE id = ? AND user_id = ?", MAPPER, id, userId)
                .stream().findFirst();
    }

    public Long insert(Long userId, String title, String description, Integer targetValue,
                       Integer manualProgress, LocalDate targetDate, String status) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO goals (user_id, title, description, target_value, manual_progress, " +
                    "target_date, status) VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, title);
            ps.setString(3, description);
            if (targetValue == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, targetValue);
            }
            ps.setInt(5, manualProgress == null ? 0 : manualProgress);
            if (targetDate == null) {
                ps.setNull(6, java.sql.Types.DATE);
            } else {
                ps.setDate(6, Date.valueOf(targetDate));
            }
            ps.setString(7, status);
            return ps;
        }, kh);
        Number key = kh.getKey();
        if (key == null) {
            throw new IllegalStateException("Goal insert did not return an id");
        }
        return key.longValue();
    }

    public int update(Long id, Long userId, String title, String description, Integer targetValue,
                      Integer manualProgress, LocalDate targetDate, String status) {
        return jdbc.update(
                "UPDATE goals SET title = ?, description = ?, target_value = ?, manual_progress = ?, " +
                "target_date = ?, status = ?, completed_at = CASE WHEN ? = 'COMPLETED' THEN CURRENT_TIMESTAMP " +
                "ELSE NULL END WHERE id = ? AND user_id = ?",
                title, description, targetValue, manualProgress == null ? 0 : manualProgress,
                targetDate == null ? null : Date.valueOf(targetDate), status, status, id, userId);
    }

    public int updateStatus(Long id, Long userId, String status) {
        return jdbc.update("UPDATE goals SET status = ?, completed_at = CASE WHEN ? = 'COMPLETED' " +
                        "THEN CURRENT_TIMESTAMP ELSE NULL END WHERE id = ? AND user_id = ?",
                status, status, id, userId);
    }

    public int updateMilestone(Long id, Long userId, int milestone) {
        return jdbc.update("UPDATE goals SET milestone_reached = ? WHERE id = ? AND user_id = ?",
                milestone, id, userId);
    }

    public int delete(Long id, Long userId) {
        return jdbc.update("DELETE FROM goals WHERE id = ? AND user_id = ?", id, userId);
    }

    public void linkHabits(Long goalId, List<Long> habitIds) {
        jdbc.update("DELETE FROM goal_habits WHERE goal_id = ?", goalId);
        if (habitIds == null) {
            return;
        }
        for (Long habitId : habitIds) {
            jdbc.update("INSERT INTO goal_habits (goal_id, habit_id) VALUES (?, ?)", goalId, habitId);
        }
    }

    public Map<Long, List<Long>> linksFor(Long userId) {
        Map<Long, List<Long>> map = new java.util.LinkedHashMap<>();
        jdbc.query("SELECT gh.goal_id, gh.habit_id FROM goal_habits gh " +
                        "JOIN goals g ON g.id = gh.goal_id WHERE g.user_id = ? ORDER BY gh.goal_id, gh.habit_id",
                rs -> {
                    map.computeIfAbsent(rs.getLong("goal_id"), k -> new java.util.ArrayList<>())
                            .add(rs.getLong("habit_id"));
                }, userId);
        return map;
    }

    /** Completions of the habits linked to one goal since the given date (or all time). */
    public int countLinkedCompletions(Long goalId, Long userId, LocalDate since) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM habit_completions hc " +
                "JOIN goal_habits gh ON gh.habit_id = hc.habit_id " +
                "WHERE gh.goal_id = ? AND hc.user_id = ? AND hc.completion_date >= ?",
                Integer.class, goalId, userId,
                Date.valueOf(since == null ? LocalDate.of(1970, 1, 1) : since));
        return c == null ? 0 : c;
    }

    public int getMilestone(Long goalId) {
        Integer m = jdbc.queryForObject("SELECT milestone_reached FROM goals WHERE id = ?", Integer.class, goalId);
        return m == null ? 0 : m;
    }

    public static Timestamp ts(LocalDate date) {
        return date == null ? null : Timestamp.valueOf(date.atStartOfDay());
    }
}
