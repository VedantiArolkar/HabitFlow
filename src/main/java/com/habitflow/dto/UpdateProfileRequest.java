package com.habitflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120) String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Please enter a valid email address") String email,

        @Size(max = 60) String timezone
) {
}
