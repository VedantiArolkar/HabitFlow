package com.habitflow.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record HabitRequest(
        @NotBlank(message = "Habit name is required")
        @Size(max = 120, message = "Habit name must be 120 characters or fewer")
        String name,

        @Size(max = 500, message = "Description must be 500 characters or fewer")
        String description,

        @NotNull(message = "Category is required")
        Long categoryId,

        @DecimalMin(value = "0.0", message = "Target must be positive")
        BigDecimal targetValue,

        @Size(max = 30) String targetUnit,

        @NotBlank(message = "Frequency is required")
        @Pattern(regexp = "DAILY|WEEKDAYS|WEEKLY|CUSTOM",
                message = "Frequency must be DAILY, WEEKDAYS, WEEKLY or CUSTOM")
        String frequency,

        @Min(value = 1, message = "Weekly target must be at least 1")
        @Max(value = 7, message = "Weekly target cannot exceed 7")
        Integer weeklyTarget,

        @Pattern(regexp = "^#([0-9a-fA-F]{6})$", message = "Color must be a hex value like #7C6BFF")
        String color,

        @Size(max = 40) String icon,

        @Pattern(regexp = "^$|^([01]\\d|2\\d):([0-5]\\d)$", message = "Reminder time must use HH:mm")
        String reminderTime,

        LocalDate startDate,

        List<@Min(1) @Max(7) Integer> scheduleDays,

        @Pattern(regexp = "ACTIVE|ARCHIVED", message = "Status must be ACTIVE or ARCHIVED")
        String status
) {
}
