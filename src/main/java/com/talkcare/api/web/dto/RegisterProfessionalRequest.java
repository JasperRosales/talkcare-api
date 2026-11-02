package com.talkcare.api.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/register/attorney} and {@code /register/psychiatrist}.
 * {@code licenseNo} is the Roll of Attorneys number or the PRC license number; {@code affiliation}
 * is the law firm or the clinic.
 */
public record RegisterProfessionalRequest(
        @NotBlank(message = RegisterRequest.REQUIRED) @Size(max = 100, message = Inputs.TOO_LONG) String username,
        @NotBlank(message = RegisterRequest.REQUIRED) @Size(max = 150, message = Inputs.TOO_LONG) String fullName,
        @NotBlank(message = RegisterRequest.REQUIRED) @Size(max = 150, message = Inputs.TOO_LONG)
        @Pattern(regexp = Inputs.EMAIL_PATTERN, message = Inputs.EMAIL_MESSAGE) String email,
        @NotBlank(message = RegisterRequest.REQUIRED) @Size(max = 50, message = Inputs.TOO_LONG) String contactNo,
        @NotBlank(message = RegisterRequest.REQUIRED) @Size(max = 500, message = Inputs.TOO_LONG) String address,
        @NotBlank(message = LICENSE_REQUIRED) @Size(max = 50, message = Inputs.TOO_LONG) String licenseNo,
        @Size(max = 150, message = Inputs.TOO_LONG) String affiliation,
        String password) {

    static final String LICENSE_REQUIRED = "Please enter your license number.";

    public RegisterProfessionalRequest {
        username = Inputs.trimToNull(username);
        fullName = Inputs.trimToNull(fullName);
        email = Inputs.trimToNull(email);
        contactNo = Inputs.trimToNull(contactNo);
        address = Inputs.trimToNull(address);
        licenseNo = Inputs.trimToNull(licenseNo);
        affiliation = Inputs.trimToNull(affiliation);
    }
}
