package com.talkcare.api.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code idDocImage} is a {@code data:image/...;base64,...} URL. */
public record VerificationRequest(
        @NotBlank(message = REQUIRED) @Size(max = 100, message = Inputs.TOO_LONG) String idType,
        @NotBlank(message = REQUIRED) @Size(max = 100, message = Inputs.TOO_LONG) String idNumber,
        @NotBlank(message = REQUIRED) @Size(max = 500, message = Inputs.TOO_LONG) String address,
        @NotBlank(message = REQUIRED) @Size(max = 150, message = Inputs.TOO_LONG)
        @Pattern(regexp = Inputs.EMAIL_PATTERN, message = Inputs.EMAIL_MESSAGE) String email,
        @NotBlank(message = REQUIRED) String idDocImage) {

    static final String REQUIRED = "ID type, ID number, address, email, and an ID image are required.";

    public VerificationRequest {
        idType = Inputs.trimToNull(idType);
        idNumber = Inputs.trimToNull(idNumber);
        address = Inputs.trimToNull(address);
        email = Inputs.trimToNull(email);
    }
}
