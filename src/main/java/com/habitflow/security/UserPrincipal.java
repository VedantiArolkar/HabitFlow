package com.habitflow.security;

import com.habitflow.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Adapts our {@link User} record to Spring Security. */
public class UserPrincipal implements UserDetails {

    private final User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    public Long getId() {
        return user.id();
    }

    public User getUser() {
        return user;
    }

    public String getFullName() {
        return user.fullName();
    }

    public String getEmail() {
        return user.email();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(user.role()));
    }

    @Override
    public String getPassword() {
        return user.passwordHash();
    }

    @Override
    public String getUsername() {
        return user.email();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
