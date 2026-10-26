package com.habitflow.model;

import java.time.LocalDateTime;

public record User(
        Long id,
        String fullName,
        String email,
        String passwordHash,
        String role,
        String timezone,
        LocalDateTime createdAt
) {
    public User withPasswordHash(String hash) {
        return new User(id, fullName, email, hash, role, timezone, createdAt);
    }
}
