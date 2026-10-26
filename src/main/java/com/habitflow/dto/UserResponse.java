package com.habitflow.dto;

import com.habitflow.model.User;

import java.time.LocalDateTime;

public record UserResponse(Long id, String fullName, String email, String role,
                           String timezone, LocalDateTime memberSince) {
    public static UserResponse from(User u) {
        return new UserResponse(u.id(), u.fullName(), u.email(), u.role(), u.timezone(), u.createdAt());
    }
}
