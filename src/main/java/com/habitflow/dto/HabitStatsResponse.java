package com.habitflow.dto;

import java.time.LocalDate;

public record HabitStatsResponse(
        Long habitId,
        String habitName,
        int totalCompletions,
        int currentStreak,
        int longestStreak,
        double weeklyRate,
        double monthlyRate,
        double overallRate,
        LocalDate lastCompleted,
        int scheduledDaysInRange,
        int completedInRange,
        String consistencyLabel
) {
}
