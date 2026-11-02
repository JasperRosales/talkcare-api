package com.talkcare.api.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
        @Size(max = 150, message = Inputs.TOO_LONG)
        @Pattern(regexp = Inputs.EMAIL_PATTERN, message = Inputs.EMAIL_MESSAGE) String email) {

    public ForgotPasswordRequest {
        email = Inputs.trimToNull(email);
    }
}
