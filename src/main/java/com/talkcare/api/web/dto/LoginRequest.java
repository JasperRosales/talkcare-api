package com.talkcare.api.web.dto;

import com.talkcare.api.domain.Role;
import jakarta.validation.constraints.Size;

/**
 * {@code username} may also be an email or full name; {@code email} is accepted as an alias.
 * {@code role} is optional: when present (the desktop's role-specific sign-in pages send it),
 * only an account with that role may sign in.
 */
public record LoginRequest(
        @Size(max = 150, message = Inputs.TOO_LONG) String username,
        @Size(max = 150, message = Inputs.TOO_LONG) String email,
        String password,
        Role role) {

    public LoginRequest {
        username = Inputs.trimToNull(username);
        email = Inputs.trimToNull(email);
    }

    public String loginId() {
        return username != null ? username : email;
    }
}
