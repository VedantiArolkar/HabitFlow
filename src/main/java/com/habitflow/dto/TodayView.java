package com.habitflow.dto;

import java.time.LocalDate;
import java.util.List;

public record TodayView(
        LocalDate date,
        boolean today,
        boolean future,
        int scheduledCount,
        int completedCount,
        int dueNowCount,
        double completionPercent,
        List<TodayHabit> habits
) {
}
