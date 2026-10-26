package com.habitflow.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Goal(
        Long id,
        Long userId,
        String title,
        String description,
        Integer targetValue,
        Integer manualProgress,
        LocalDate targetDate,
        String status,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
