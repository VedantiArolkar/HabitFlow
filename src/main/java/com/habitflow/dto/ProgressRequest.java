package com.habitflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProgressRequest(@NotNull(message = "Progress is required")
                              @Min(value = 0, message = "Progress cannot be negative") Integer progress) {
}
