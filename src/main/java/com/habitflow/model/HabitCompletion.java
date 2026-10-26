package com.habitflow.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record HabitCompletion(
        Long id,
        Long habitId,
        Long userId,
        LocalDate completionDate,
        String note,
        LocalDateTime createdAt
) {
}
