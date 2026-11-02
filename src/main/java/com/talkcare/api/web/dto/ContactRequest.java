package com.talkcare.api.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/contact}. */
public record ContactRequest(
        @NotBlank(message = REQUIRED) @Size(max = 150, message = Inputs.TOO_LONG) String name,
        @NotBlank(message = REQUIRED) @Size(max = 150, message = Inputs.TOO_LONG)
        @Pattern(regexp = Inputs.EMAIL_PATTERN, message = Inputs.EMAIL_MESSAGE) String email,
        @NotBlank(message = REQUIRED) @Size(max = 2000, message = Inputs.TOO_LONG) String message) {

    static final String REQUIRED = "Please enter your name, email and message.";

    public ContactRequest {
        name = Inputs.trimToNull(name);
        email = Inputs.trimToNull(email);
        message = Inputs.trimToNull(message);
    }
}
