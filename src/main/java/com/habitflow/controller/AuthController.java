package com.habitflow.controller;

import com.habitflow.dto.RegisterRequest;
import com.habitflow.dto.UserResponse;
import com.habitflow.security.CurrentUser;
import com.habitflow.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final CurrentUser currentUser;
    private final CookieCsrfTokenRepository csrfTokenRepository;

    public AuthController(UserService userService, CurrentUser currentUser,
                          CookieCsrfTokenRepository csrfTokenRepository) {
        this.userService = userService;
        this.currentUser = currentUser;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    /** Creates the account. Login itself is handled by Spring Security's form login filter. */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.from(currentUser.user());
    }

    /**
     * Hands the SPA a CSRF token and makes sure the readable XSRF-TOKEN cookie exists.
     * Needed because a fresh browser has no cookie yet and the first POST would be rejected.
     */
    @GetMapping("/csrf")
    public Map<String, String> csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.loadToken(request);
        if (token == null) {
            token = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(token, request, response);
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("headerName", token.getHeaderName());
        body.put("token", token.getToken());
        return body;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "application", "HabitFlow");
    }
}
