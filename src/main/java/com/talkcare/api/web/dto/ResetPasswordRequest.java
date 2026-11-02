package com.talkcare.api.web.dto;

import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(@Size(max = 200, message = Inputs.TOO_LONG) String token, String newPassword) {

    public ResetPasswordRequest {
        token = Inputs.trimToNull(token);
    }
}
