package com.habitflow.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A habit owned by exactly one user. {@code scheduleDays} holds ISO day numbers
 * (1 = Monday ... 7 = Sunday) and is what the streak engine uses to decide which
 * days were actually scheduled - unscheduled days never count as "missed".
 */
public record Habit(
        Long id,
        Long userId,
        Long categoryId,
        String categoryName,
        String categoryColor,
        String name,
        String description,
        BigDecimal targetValue,
        String targetUnit,
        String frequency,
        Integer weeklyTarget,
        String color,
        String icon,
        LocalTime reminderTime,
        LocalDate startDate,
        String status,
        Set<Integer> scheduleDays,
        LocalDateTime createdAt
) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public Habit withScheduleDays(Set<Integer> days) {
        return new Habit(id, userId, categoryId, categoryName, categoryColor, name, description,
                targetValue, targetUnit, frequency, weeklyTarget, color, icon, reminderTime,
                startDate, status, new LinkedHashSet<>(days), createdAt);
    }
}
