package com.habitflow.repository;

import com.habitflow.model.Habit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class HabitRepository {

    private static final String SELECT_BASE =
            "SELECT h.id, h.user_id, h.category_id, c.name AS category_name, c.color AS category_color, " +
            "h.name, h.description, h.target_value, h.target_unit, h.frequency, h.weekly_target, " +
            "h.color, h.icon, h.reminder_time, h.start_date, h.status, h.created_at " +
            "FROM habits h JOIN habit_categories c ON c.id = h.category_id ";

    private static final RowMapper<Habit> MAPPER = (rs, i) -> new Habit(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getLong("category_id"),
            rs.getString("category_name"),
            rs.getString("category_color"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getBigDecimal("target_value"),
            rs.getString("target_unit"),
            rs.getString("frequency"),
            (Integer) rs.getObject("weekly_target"),
            rs.getString("color"),
            rs.getString("icon"),
            rs.getTime("reminder_time") == null ? null : rs.getTime("reminder_time").toLocalTime(),
            rs.getDate("start_date").toLocalDate(),
            rs.getString("status"),
            new LinkedHashSet<>(),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public HabitRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Habit> findByUser(Long userId, String status) {
        List<Habit> habits;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            habits = jdbc.query(SELECT_BASE + "WHERE h.user_id = ? ORDER BY h.created_at DESC", MAPPER, userId);
        } else {
            habits = jdbc.query(SELECT_BASE + "WHERE h.user_id = ? AND h.status = ? ORDER BY h.created_at DESC",
                    MAPPER, userId, status);
        }
        return attachSchedules(habits);
    }

    public Optional<Habit> findByIdAndUser(Long id, Long userId) {
        List<Habit> rows = jdbc.query(SELECT_BASE + "WHERE h.id = ? AND h.user_id = ?", MAPPER, id, userId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(attachSchedules(rows).get(0));
    }

    public boolean existsForUser(Long id, Long userId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM habits WHERE id = ? AND user_id = ?",
                Integer.class, id, userId);
        return c != null && c > 0;
    }

    public Long insert(Long userId, Long categoryId, String name, String description, BigDecimal targetValue,
                       String targetUnit, String frequency, Integer weeklyTarget, String color, String icon,
                       LocalTime reminderTime, LocalDate startDate, String status) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO habits (user_id, category_id, name, description, target_value, target_unit, " +
                    "frequency, weekly_target, color, icon, reminder_time, start_date, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setLong(2, categoryId);
            ps.setString(3, name);
            ps.setString(4, description);
            ps.setBigDecimal(5, targetValue);
            ps.setString(6, targetUnit);
            ps.setString(7, frequency);
            if (weeklyTarget == null) {
                ps.setNull(8, java.sql.Types.INTEGER);
            } else {
                ps.setInt(8, weeklyTarget);
            }
            ps.setString(9, color);
            ps.setString(10, icon);
            if (reminderTime == null) {
                ps.setNull(11, java.sql.Types.TIME);
            } else {
                ps.setTime(11, Time.valueOf(reminderTime));
            }
            ps.setDate(12, Date.valueOf(startDate));
            ps.setString(13, status);
            return ps;
        }, kh);
        Number key = kh.getKey();
        if (key == null) {
            throw new IllegalStateException("Habit insert did not return an id");
        }
        return key.longValue();
    }

    public int update(Long habitId, Long userId, Long categoryId, String name, String description,
                      BigDecimal targetValue, String targetUnit, String frequency, Integer weeklyTarget,
                      String color, String icon, LocalTime reminderTime, LocalDate startDate, String status) {
        return jdbc.update(
                "UPDATE habits SET category_id = ?, name = ?, description = ?, target_value = ?, target_unit = ?, " +
                "frequency = ?, weekly_target = ?, color = ?, icon = ?, reminder_time = ?, start_date = ?, status = ? " +
                "WHERE id = ? AND user_id = ?",
                categoryId, name, description, targetValue, targetUnit, frequency, weeklyTarget, color, icon,
                reminderTime == null ? null : Time.valueOf(reminderTime),
                Date.valueOf(startDate), status, habitId, userId);
    }

    public int updateStatus(Long habitId, Long userId, String status) {
        return jdbc.update("UPDATE habits SET status = ? WHERE id = ? AND user_id = ?", status, habitId, userId);
    }

    public int delete(Long habitId, Long userId) {
        return jdbc.update("DELETE FROM habits WHERE id = ? AND user_id = ?", habitId, userId);
    }

    public void replaceSchedule(Long habitId, Set<Integer> days) {
        jdbc.update("DELETE FROM habit_schedules WHERE habit_id = ?", habitId);
        if (days == null) {
            return;
        }
        for (Integer day : days) {
            jdbc.update("INSERT INTO habit_schedules (habit_id, day_of_week) VALUES (?, ?)", habitId, day);
        }
    }

    private Map<Long, Set<Integer>> schedulesFor(Set<Long> habitIds) {
        Map<Long, Set<Integer>> map = new LinkedHashMap<>();
        if (habitIds.isEmpty()) {
            return map;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(habitIds.size(), "?"));
        jdbc.query("SELECT habit_id, day_of_week FROM habit_schedules WHERE habit_id IN (" + placeholders + ")",
                rs -> {
                    map.computeIfAbsent(rs.getLong("habit_id"), k -> new LinkedHashSet<>())
                            .add(rs.getInt("day_of_week"));
                }, habitIds.toArray());
        return map;
    }

    private List<Habit> attachSchedules(List<Habit> habits) {
        if (habits.isEmpty()) {
            return habits;
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Habit h : habits) {
            ids.add(h.id());
        }
        Map<Long, Set<Integer>> schedules = schedulesFor(ids);
        List<Habit> result = new ArrayList<>(habits.size());
        for (Habit h : habits) {
            result.add(h.withScheduleDays(schedules.getOrDefault(h.id(), new LinkedHashSet<>())));
        }
        return result;
    }
}
