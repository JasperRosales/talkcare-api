package com.talkcare.api.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.talkcare.api.domain.Role;
import com.talkcare.api.domain.ServiceType;
import com.talkcare.api.domain.UserStatus;
import com.talkcare.api.domain.account.Account;
import com.talkcare.api.domain.account.Administrator;
import com.talkcare.api.domain.account.Attorney;
import com.talkcare.api.domain.account.Client;
import com.talkcare.api.domain.account.Psychiatrist;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * The signed-in account (also each row of the admin user list). Subtype fields are flattened
 * into the same object; {@code sessionToken} is present only in login/register responses.
 */
public record AccountResponse(
        int id,
        String username,
        String email,
        String fullName,
        String contactNo,
        String address,
        String profilePictureUrl,
        Role role,
        String roleDisplayName,
        UserStatus status,
        String dashboardView,
        Set<String> permissions,
        String idType,
        String idNumber,
        String verificationStatus,
        Instant createdAt,
        @JsonUnwrapped Details details,
        @JsonInclude(JsonInclude.Include.NON_NULL) String sessionToken) {

    public static AccountResponse from(Account account) {
        return withSession(account, null);
    }

    public static AccountResponse withSession(Account account, String sessionToken) {
        Account.Verification verification = account.verification();
        return new AccountResponse(
                account.id(),
                account.username(),
                account.email(),
                account.profile().fullName(),
                account.profile().contactNo(),
                account.profile().address(),
                account.profile().profilePictureUrl(),
                account.role(),
                account.roleDisplayName(),
                account.status(),
                account.dashboardView(),
                account.permissions(),
                verification == null ? "" : verification.idType(),
                verification == null ? "" : verification.idNumber(),
                verification == null ? null : verification.status(),
                account.createdAt(),
                Details.of(account),
                sessionToken);
    }

    /** Role-specific fields. */
    public sealed interface Details {

        static Details of(Account account) {
            return switch (account) {
                case Client c -> new ClientDetails(c.preferredService(), c.emergencyContactName(), c.emergencyContactNo());
                case Attorney a -> new AttorneyDetails(a.serviceType(), a.displayTitle(), a.bio(), a.yearsOfExperience(),
                        a.isAcceptingClients(), a.barRollNo(), a.ibpNo(), a.lawFirm(), a.jurisdiction());
                case Psychiatrist p -> new PsychiatristDetails(p.serviceType(), p.displayTitle(), p.bio(),
                        p.yearsOfExperience(), p.isAcceptingClients(), p.prcLicenseNo(), p.licenseExpiry(),
                        p.clinicName(), p.acceptsMedicationManagement());
                case Administrator ad -> new AdministratorDetails(ad.jobTitle(), ad.department(), ad.isSuperAdmin());
            };
        }
    }

    public record ClientDetails(ServiceType preferredService, String emergencyContactName, String emergencyContactNo)
            implements Details {
    }

    public record AttorneyDetails(
            ServiceType serviceType, String displayTitle, String bio, Integer yearsOfExperience,
            boolean acceptingClients, String barRollNo, String ibpNo, String lawFirm, String jurisdiction)
            implements Details {
    }

    public record PsychiatristDetails(
            ServiceType serviceType, String displayTitle, String bio, Integer yearsOfExperience,
            boolean acceptingClients, String prcLicenseNo, LocalDate licenseExpiry, String clinicName,
            boolean acceptsMedicationManagement)
            implements Details {
    }

    public record AdministratorDetails(String jobTitle, String department, @JsonProperty("isSuperAdmin") boolean superAdmin)
            implements Details {
    }
}
