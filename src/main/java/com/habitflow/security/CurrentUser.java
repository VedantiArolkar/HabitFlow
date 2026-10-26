package com.habitflow.security;

import com.habitflow.exception.ForbiddenException;
import com.habitflow.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated user for controllers and services. Every service method
 * that touches user data takes the id from here - never from the request body - which
 * is what prevents one user from reading or changing another user's habits.
 */
@Component
public class CurrentUser {

    public UserPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal up)) {
            throw new ForbiddenException("You must be signed in to do that.");
        }
        return up;
    }

    public Long id() {
        return principal().getId();
    }

    public User user() {
        return principal().getUser();
    }
}
