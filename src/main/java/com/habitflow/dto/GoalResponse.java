package com.habitflow.dto;

import java.time.LocalDate;
import java.util.List;

public record GoalResponse(
        Long id,
        String title,
        String description,
        Integer targetValue,
        int progress,
        double percent,
        LocalDate targetDate,
        long daysRemaining,
        String status,
        List<Long> habitIds,
        List<String> habitNames,
        String milestoneMessage,
        int milestoneReached,
        String source
) {
}
