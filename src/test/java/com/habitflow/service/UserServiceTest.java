package com.habitflow.service;

import com.habitflow.dto.ChangePasswordRequest;
import com.habitflow.dto.RegisterRequest;
import com.habitflow.dto.UserResponse;
import com.habitflow.exception.BadRequestException;
import com.habitflow.exception.ConflictException;
import com.habitflow.model.User;
import com.habitflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Registration and password tests. The encoder is a real BCrypt instance, so the
 * "never store a plain-text password" requirement is actually exercised.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock private UserRepository userRepository;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // low cost = fast tests
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, encoder);
    }

    @Test
    @DisplayName("registration hashes the password with BCrypt")
    void registerHashesPassword() {
        when(userRepository.existsByEmail("aarav@example.com")).thenReturn(false);
        when(userRepository.insert(eq("Aarav Sharma"), eq("aarav@example.com"), anyString(), eq("UTC")))
                .thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User(1L, "Aarav Sharma",
                "aarav@example.com", "$2a$04$abcdefghijklmnopqrstuv", "ROLE_USER", "UTC", LocalDateTime.now())));

        UserResponse response = userService.register(
                new RegisterRequest("Aarav Sharma", "aarav@example.com", "Secret123"));

        assertEquals("aarav@example.com", response.email());
        verify(userRepository).insert(eq("Aarav Sharma"), eq("aarav@example.com"), anyString(), eq("UTC"));
    }

    @Test
    @DisplayName("the stored hash is never the raw password and matches it")
    void encodedHashVerifies() {
        String hash = encoder.encode("Secret123");
        assertFalse(hash.contains("Secret123"));
        assertTrue(encoder.matches("Secret123", hash));
    }

    @Test
    @DisplayName("duplicate email registration is rejected with 409")
    void duplicateEmailConflicts() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> userService.register(
                new RegisterRequest("Someone", "taken@example.com", "Secret123")));
        verify(userRepository, never()).insert(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("email is normalised to lower case before saving")
    void emailIsNormalised() {
        when(userRepository.existsByEmail("mixed@example.com")).thenReturn(false);
        when(userRepository.insert(anyString(), eq("mixed@example.com"), anyString(), anyString())).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(new User(2L, "Mixed Case",
                "mixed@example.com", "hash", "ROLE_USER", "UTC", LocalDateTime.now())));

        userService.register(new RegisterRequest("Mixed Case", "  MiXeD@Example.COM ", "Secret123"));

        verify(userRepository).existsByEmail("mixed@example.com");
    }

    @Test
    @DisplayName("changing a password with the wrong current password fails")
    void wrongCurrentPasswordFails() {
        String hash = encoder.encode("Correct123");
        when(userRepository.findById(3L)).thenReturn(Optional.of(new User(3L, "Aarav", "a@example.com",
                hash, "ROLE_USER", "UTC", LocalDateTime.now())));

        assertThrows(BadRequestException.class, () -> userService.changePassword(3L,
                new ChangePasswordRequest("WrongPass1", "BrandNew123")));
        verify(userRepository, never()).updatePassword(org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    @Test
    @DisplayName("reusing the same password is rejected")
    void samePasswordRejected() {
        String hash = encoder.encode("Correct123");
        when(userRepository.findById(4L)).thenReturn(Optional.of(new User(4L, "Aarav", "a@example.com",
                hash, "ROLE_USER", "UTC", LocalDateTime.now())));

        assertThrows(BadRequestException.class, () -> userService.changePassword(4L,
                new ChangePasswordRequest("Correct123", "Correct123")));
    }

    @Test
    @DisplayName("a correct current password writes a fresh BCrypt hash")
    void changePasswordStoresNewHash() {
        String hash = encoder.encode("Correct123");
        when(userRepository.findById(5L)).thenReturn(Optional.of(new User(5L, "Aarav", "a@example.com",
                hash, "ROLE_USER", "UTC", LocalDateTime.now())));

        userService.changePassword(5L, new ChangePasswordRequest("Correct123", "BrandNew123"));

        verify(userRepository).updatePassword(eq(5L), anyString());
    }
}
