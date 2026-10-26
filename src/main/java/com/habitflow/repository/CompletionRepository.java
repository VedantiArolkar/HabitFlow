package com.habitflow.repository;

import com.habitflow.model.HabitCompletion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class CompletionRepository {

    private static final RowMapper<HabitCompletion> MAPPER = (rs, i) -> new HabitCompletion(
            rs.getLong("id"),
            rs.getLong("habit_id"),
            rs.getLong("user_id"),
            rs.getDate("completion_date").toLocalDate(),
            rs.getString("note"),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public CompletionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<HabitCompletion> findByHabitAndDate(Long habitId, LocalDate date) {
        return jdbc.query("SELECT id, habit_id, user_id, completion_date, note, created_at " +
                        "FROM habit_completions WHERE habit_id = ? AND completion_date = ?",
                MAPPER, habitId, Date.valueOf(date)).stream().findFirst();
    }

    public boolean exists(Long habitId, LocalDate date) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM habit_completions WHERE habit_id = ? AND completion_date = ?",
                Integer.class, habitId, Date.valueOf(date));
        return c != null && c > 0;
    }

    /**
     * Inserts a completion. The UNIQUE(habit_id, completion_date) constraint is the last line of
     * defence against duplicates; the service checks first so the user gets a friendly 409 instead
     * of a database error.
     */
    public Long insert(Long habitId, Long userId, LocalDate date, String note) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO habit_completions (habit_id, user_id, completion_date, note) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, habitId);
            ps.setLong(2, userId);
            ps.setDate(3, Date.valueOf(date));
            ps.setString(4, note);
            return ps;
        }, kh);
        Number key = kh.getKey();
        if (key == null) {
            throw new IllegalStateException("Completion insert did not return an id");
        }
        return key.longValue();
    }

    public int delete(Long habitId, Long userId, LocalDate date) {
        return jdbc.update("DELETE FROM habit_completions WHERE habit_id = ? AND user_id = ? AND completion_date = ?",
                habitId, userId, Date.valueOf(date));
    }

    public int deleteById(Long id, Long userId) {
        return jdbc.update("DELETE FROM habit_completions WHERE id = ? AND user_id = ?", id, userId);
    }

    public List<HabitCompletion> findByUserAndDate(Long userId, LocalDate date) {
        return jdbc.query("SELECT id, habit_id, user_id, completion_date, note, created_at " +
                        "FROM habit_completions WHERE user_id = ? AND completion_date = ? ORDER BY created_at DESC",
                MAPPER, userId, Date.valueOf(date));
    }

    public List<HabitCompletion> findByUserAndRange(Long userId, LocalDate from, LocalDate to) {
        return jdbc.query("SELECT id, habit_id, user_id, completion_date, note, created_at " +
                        "FROM habit_completions WHERE user_id = ? AND completion_date BETWEEN ? AND ? " +
                        "ORDER BY completion_date", MAPPER, userId, Date.valueOf(from), Date.valueOf(to));
    }

    public List<HabitCompletion> findByHabit(Long habitId, Long userId) {
        return jdbc.query("SELECT id, habit_id, user_id, completion_date, note, created_at " +
                        "FROM habit_completions WHERE habit_id = ? AND user_id = ? ORDER BY completion_date",
                MAPPER, habitId, userId);
    }

    /** Completion dates for every habit of one user, grouped in memory once per request. */
    public Set<LocalDate> datesForHabit(Long habitId, Long userId) {
        List<LocalDate> dates = jdbc.queryForList(
                "SELECT completion_date FROM habit_completions WHERE habit_id = ? AND user_id = ?",
                LocalDate.class, habitId, userId);
        return new HashSet<>(dates);
    }

    public List<LocalDate> datesForUserBetween(Long userId, LocalDate from, LocalDate to) {
        return jdbc.queryForList("SELECT completion_date FROM habit_completions " +
                        "WHERE user_id = ? AND completion_date BETWEEN ? AND ?",
                LocalDate.class, userId, Date.valueOf(from), Date.valueOf(to));
    }

    public int countForUser(Long userId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM habit_completions WHERE user_id = ?",
                Integer.class, userId);
        return c == null ? 0 : c;
    }

    public int countForUserOnDate(Long userId, LocalDate date) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM habit_completions WHERE user_id = ? AND completion_date = ?",
                Integer.class, userId, Date.valueOf(date));
        return c == null ? 0 : c;
    }

    public int countForHabit(Long habitId, Long userId) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM habit_completions WHERE habit_id = ? AND user_id = ?",
                Integer.class, habitId, userId);
        return c == null ? 0 : c;
    }

    public Optional<LocalDate> lastCompletionDate(Long habitId, Long userId) {
        List<java.sql.Date> rows = jdbc.queryForList(
                "SELECT MAX(completion_date) AS d FROM habit_completions WHERE habit_id = ? AND user_id = ?",
                java.sql.Date.class, habitId, userId);
        if (rows.isEmpty() || rows.get(0) == null) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0).toLocalDate());
    }

    /** Creation timestamps of completions in a range - used for "best time of day" insights. */
    public List<java.time.LocalDateTime> createdTimestamps(Long userId, LocalDate from, LocalDate to) {
        return jdbc.query("SELECT created_at FROM habit_completions " +
                        "WHERE user_id = ? AND completion_date BETWEEN ? AND ?",
                (rs, i) -> rs.getTimestamp("created_at").toLocalDateTime(),
                userId, Date.valueOf(from), Date.valueOf(to));
    }

    /** Completion counts per habit for one user inside a range. */
    public java.util.Map<Long, Integer> countByHabit(Long userId, LocalDate from, LocalDate to) {
        java.util.Map<Long, Integer> map = new java.util.LinkedHashMap<>();
        jdbc.query("SELECT habit_id, COUNT(*) AS c FROM habit_completions " +
                        "WHERE user_id = ? AND completion_date BETWEEN ? AND ? GROUP BY habit_id",
                rs -> { map.put(rs.getLong("habit_id"), rs.getInt("c")); },
                userId, Date.valueOf(from), Date.valueOf(to));
        return map;
    }
}
