package com.habitflow.controller;

import com.habitflow.dto.ApiMessage;
import com.habitflow.dto.ChangePasswordRequest;
import com.habitflow.dto.UpdateProfileRequest;
import com.habitflow.dto.UserResponse;
import com.habitflow.security.CurrentUser;
import com.habitflow.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserService userService;
    private final CurrentUser currentUser;

    public ProfileController(UserService userService, CurrentUser currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public UserResponse profile() {
        return userService.getProfile(currentUser.id());
    }

    @PutMapping
    public UserResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(currentUser.id(), request);
    }

    /** After a password change the session is invalidated so the user re-authenticates. */
    @PutMapping("/password")
    public ApiMessage changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                     HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        userService.changePassword(currentUser.id(), request);
        new SecurityContextLogoutHandler().logout(httpRequest, httpResponse,
                SecurityContextHolder.getContext().getAuthentication());
        return ApiMessage.ok("Password updated. Please sign in again.");
    }
}
