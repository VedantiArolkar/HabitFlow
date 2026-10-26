package com.habitflow.service;

import com.habitflow.dto.ChangePasswordRequest;
import com.habitflow.dto.RegisterRequest;
import com.habitflow.dto.UpdateProfileRequest;
import com.habitflow.dto.UserResponse;
import com.habitflow.exception.BadRequestException;
import com.habitflow.exception.ConflictException;
import com.habitflow.exception.NotFoundException;
import com.habitflow.model.User;
import com.habitflow.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalise(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with that email already exists.");
        }
        String hash = passwordEncoder.encode(request.password());
        Long id = userRepository.insert(request.fullName().trim(), email, hash, "UTC");
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("User vanished after insert"));
        return UserResponse.from(user);
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Account not found."));
    }

    public UserResponse getProfile(Long userId) {
        return UserResponse.from(getById(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        String email = normalise(request.email());
        User current = getById(userId);
        if (!current.email().equalsIgnoreCase(email) && userRepository.existsByEmail(email)) {
            throw new ConflictException("That email is already used by another account.");
        }
        userRepository.updateProfile(userId, request.fullName().trim(), email, request.timezone());
        return UserResponse.from(getById(userId));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User current = getById(userId);
        if (!passwordEncoder.matches(request.currentPassword(), current.passwordHash())) {
            throw new BadRequestException("Your current password is incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), current.passwordHash())) {
            throw new BadRequestException("The new password must be different from the current one.");
        }
        userRepository.updatePassword(userId, passwordEncoder.encode(request.newPassword()));
    }

    private String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
