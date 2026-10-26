package com.habitflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120, message = "Full name must be between 2 and 120 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Please enter a valid email address")
        @Size(max = 160)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
        @Pattern(regexp = ".*[A-Za-z].*", message = "Password must contain at least one letter")
        @Pattern(regexp = ".*[0-9].*", message = "Password must contain at least one number")
        String password
) {
}
