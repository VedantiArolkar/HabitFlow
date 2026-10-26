package com.habitflow.dto;

import com.habitflow.model.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(Long id, String type, String title, String message,
                                  String link, boolean read, LocalDateTime createdAt) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.id(), n.type(), n.title(), n.message(),
                n.link(), n.read(), n.createdAt());
    }
}
