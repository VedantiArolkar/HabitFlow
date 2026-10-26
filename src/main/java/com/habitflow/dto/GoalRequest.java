package com.habitflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record GoalRequest(
        @NotBlank(message = "Goal title is required")
        @Size(max = 140, message = "Goal title must be 140 characters or fewer") String title,

        @Size(max = 500) String description,

        @Min(value = 1, message = "Target value must be at least 1") Integer targetValue,

        @Min(value = 0, message = "Progress cannot be negative") Integer manualProgress,

        LocalDate targetDate,

        List<Long> habitIds,

        String status
) {
}
