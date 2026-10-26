package com.habitflow.dto;

import java.time.LocalDate;

/** One habit row as rendered on the dashboard / tracking screen. */
public record TodayHabit(
        Long habitId,
        String name,
        String icon,
        String color,
        String categoryName,
        String frequency,
        String reminderTime,
        boolean scheduled,
        boolean completed,
        String note,
        int currentStreak,
        String description
) {
    public static TodayHabit of(Long habitId, String name, String icon, String color, String categoryName,
                                String frequency, String reminderTime, boolean scheduled, boolean completed,
                                String note, int currentStreak, String description) {
        return new TodayHabit(habitId, name, icon, color, categoryName, frequency, reminderTime,
                scheduled, completed, note, currentStreak, description);
    }
}
