package com.talkcare.api.domain.verification;

import java.time.Instant;

/**
 * An identity verification awaiting review, with the applicant's profile details. {@code role} is
 * the applicant's role code; {@code licenseNo} is the bar roll / PRC license number for professionals.
 */
public record PendingVerification(
        int id,
        int userId,
        String role,
        String licenseNo,
        String fullName,
        String email,
        String idType,
        String idNumber,
        String address,
        String contactNo,
        String documentUrl,
        VerificationStatus status,
        Instant submittedAt) {
}
