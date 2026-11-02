package com.talkcare.api.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code serviceType} is only used when the concern type is not tied to a service. */
public record CreateReportRequest(
        @NotBlank(message = REQUIRED) @Size(max = 150, message = Inputs.TOO_LONG) String fullName,
        @NotBlank(message = REQUIRED) @Size(max = 150, message = Inputs.TOO_LONG)
        @Pattern(regexp = Inputs.EMAIL_PATTERN, message = Inputs.EMAIL_MESSAGE) String email,
        @NotBlank(message = REQUIRED) @Size(max = 500, message = Inputs.TOO_LONG) String address,
        @NotBlank(message = REQUIRED) @Size(max = 50, message = Inputs.TOO_LONG) String contactNo,
        @NotBlank(message = REQUIRED) @Size(max = 100, message = Inputs.TOO_LONG) String concernType,
        @NotBlank(message = REQUIRED) @Size(max = 10000, message = Inputs.TOO_LONG) String concernDetails,
        @Size(max = 150, message = Inputs.TOO_LONG) String involvedWitness,
        String serviceType) {

    static final String REQUIRED = "Please complete all required report fields.";

    public CreateReportRequest {
        fullName = Inputs.trimToNull(fullName);
        email = Inputs.trimToNull(email);
        address = Inputs.trimToNull(address);
        contactNo = Inputs.trimToNull(contactNo);
        concernType = Inputs.trimToNull(concernType);
        concernDetails = Inputs.trimToNull(concernDetails);
        involvedWitness = Inputs.trimToNull(involvedWitness);
    }
}
