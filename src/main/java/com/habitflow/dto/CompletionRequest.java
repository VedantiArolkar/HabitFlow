package com.habitflow.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CompletionRequest(
        @NotNull(message = "Date is required") LocalDate date,
        @Size(max = 500, message = "Note must be 500 characters or fewer") String note,
        Boolean allowFuture
) {
}
