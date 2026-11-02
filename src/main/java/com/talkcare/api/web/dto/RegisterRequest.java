package com.talkcare.api.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = REQUIRED) @Size(max = 100, message = Inputs.TOO_LONG) String username,
        @NotBlank(message = REQUIRED) @Size(max = 150, message = Inputs.TOO_LONG) String fullName,
        @NotBlank(message = REQUIRED) @Size(max = 150, message = Inputs.TOO_LONG)
        @Pattern(regexp = Inputs.EMAIL_PATTERN, message = Inputs.EMAIL_MESSAGE) String email,
        @NotBlank(message = REQUIRED) @Size(max = 50, message = Inputs.TOO_LONG) String contactNo,
        @NotBlank(message = REQUIRED) @Size(max = 500, message = Inputs.TOO_LONG) String address,
        String password) {

    static final String REQUIRED = "Please complete all required account fields.";

    public RegisterRequest {
        username = Inputs.trimToNull(username);
        fullName = Inputs.trimToNull(fullName);
        email = Inputs.trimToNull(email);
        contactNo = Inputs.trimToNull(contactNo);
        address = Inputs.trimToNull(address);
    }
}
