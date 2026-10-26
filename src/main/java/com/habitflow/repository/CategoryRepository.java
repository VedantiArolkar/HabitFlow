package com.habitflow.repository;

import com.habitflow.model.HabitCategory;
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
public class CategoryRepository {

    private static final RowMapper<HabitCategory> MAPPER = (rs, i) -> new HabitCategory(
            rs.getLong("id"), rs.getString("name"), rs.getString("color"),
            rs.getString("icon"), (Long) rs.getObject("owner_user_id"));

    private final JdbcTemplate jdbc;

    public CategoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Global (seeded, owner NULL) plus the user's own custom categories. */
    public List<HabitCategory> findVisibleTo(Long userId) {
        return jdbc.query(
                "SELECT id, name, color, icon, owner_user_id FROM habit_categories " +
                        "WHERE owner_user_id IS NULL OR owner_user_id = ? ORDER BY name", MAPPER, userId);
    }

    public Optional<HabitCategory> findById(Long id) {
        return jdbc.query("SELECT id, name, color, icon, owner_user_id FROM habit_categories WHERE id = ?",
                MAPPER, id).stream().findFirst();
    }

    public boolean isVisibleTo(Long categoryId, Long userId) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM habit_categories WHERE id = ? AND (owner_user_id IS NULL OR owner_user_id = ?)",
                Integer.class, categoryId, userId);
        return c != null && c > 0;
    }

    public Long insert(String name, String color, String icon, Long ownerUserId) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO habit_categories (name, color, icon, owner_user_id) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, color);
            ps.setString(3, icon);
            if (ownerUserId == null) {
                ps.setNull(4, java.sql.Types.BIGINT);
            } else {
                ps.setLong(4, ownerUserId);
            }
            return ps;
        }, kh);
        Number key = kh.getKey();
        if (key == null) {
            throw new IllegalStateException("Category insert did not return an id");
        }
        return key.longValue();
    }

    public int deleteOwned(Long id, Long userId) {
        return jdbc.update("DELETE FROM habit_categories WHERE id = ? AND owner_user_id = ?", id, userId);
    }
}
