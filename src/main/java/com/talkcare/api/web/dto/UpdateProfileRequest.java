package com.talkcare.api.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Omitted or blank fields keep their value, except {@code contactNo} and {@code address}: sent
 * as an empty string, they are cleared. An empty {@code password} keeps the current one.
 */
public record UpdateProfileRequest(
        @Size(max = 100, message = Inputs.TOO_LONG) String username,
        @Size(max = 150, message = Inputs.TOO_LONG)
        @Pattern(regexp = Inputs.EMAIL_PATTERN, message = Inputs.EMAIL_MESSAGE) String email,
        String password,
        @Size(max = 150, message = Inputs.TOO_LONG) String fullName,
        @Size(max = 50, message = Inputs.TOO_LONG) String contactNo,
        @Size(max = 500, message = Inputs.TOO_LONG) String address) {

    public UpdateProfileRequest {
        username = Inputs.trimToNull(username);
        email = Inputs.trimToNull(email);
        fullName = Inputs.trimToNull(fullName);
        contactNo = Inputs.trim(contactNo);
        address = Inputs.trim(address);
    }
}
