package com.habitflow.model;

import java.time.LocalDateTime;

public record Notification(
        Long id,
        Long userId,
        String type,
        String title,
        String message,
        String link,
        boolean read,
        LocalDateTime createdAt
) {
}
