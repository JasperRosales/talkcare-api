package com.talkcare.api.repository;

import com.talkcare.api.domain.Role;
import com.talkcare.api.domain.ServiceType;
import com.talkcare.api.domain.UserStatus;
import com.talkcare.api.domain.account.Account;
import com.talkcare.api.domain.account.Administrator;
import com.talkcare.api.domain.account.Attorney;
import com.talkcare.api.domain.account.Client;
import com.talkcare.api.domain.account.Professional;
import com.talkcare.api.domain.account.Psychiatrist;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.RowMapper;

/**
 * Builds the {@link Account} subtype named by {@code roles.code}. The database triggers guarantee
 * the matching subtype row exists, so the subtype columns are read without further checks.
 */
final class AccountRowMapper implements RowMapper<Account> {

    private final Map<Role, Set<String>> permissionsByRole;

    AccountRowMapper(Map<Role, Set<String>> permissionsByRole) {
        this.permissionsByRole = permissionsByRole;
    }

    @Override
    public Account mapRow(ResultSet rs, int rowNum) throws SQLException {
        Role role = Rows.enumValue(rs, "role_code", Role.class);
        Account.Identity identity = identity(rs);
        Set<String> permissions = permissionsByRole.getOrDefault(role, Set.of());
        return switch (role) {
            case CLIENT -> new Client(identity, permissions,
                    Rows.enumValue(rs, "client_preferred_service", ServiceType.class),
                    Rows.text(rs, "client_emergency_contact_name"),
                    Rows.text(rs, "client_emergency_contact_no"));
            case ATTORNEY -> new Attorney(identity, permissions, professional(rs),
                    Rows.text(rs, "atty_bar_roll_no"),
                    Rows.text(rs, "atty_ibp_no"),
                    Rows.text(rs, "atty_law_firm"),
                    Rows.text(rs, "atty_jurisdiction"));
            case PSYCHIATRIST -> new Psychiatrist(identity, permissions, professional(rs),
                    Rows.text(rs, "psy_prc_license_no"),
                    Rows.date(rs, "psy_license_expiry"),
                    Rows.text(rs, "psy_clinic_name"),
                    Rows.bool(rs, "psy_accepts_medication_management", true));
            case ADMIN -> new Administrator(identity, permissions,
                    Rows.text(rs, "admin_job_title"),
                    Rows.text(rs, "admin_department"),
                    Rows.bool(rs, "admin_is_super_admin", false));
        };
    }

    private static Account.Identity identity(ResultSet rs) throws SQLException {
        Account.Profile profile = new Account.Profile(
                Rows.text(rs, "full_name"),
                Rows.text(rs, "contact_no"),
                Rows.text(rs, "address"),
                Rows.date(rs, "birth_date"),
                Rows.text(rs, "profile_picture_url"));
        Account.Verification verification = rs.getString("verification_status") == null ? null
                : new Account.Verification(
                        rs.getString("verification_status"),
                        Rows.text(rs, "verification_id_type"),
                        Rows.text(rs, "verification_id_number"),
                        rs.getString("verification_rejection_reason"),
                        Rows.instant(rs, "verification_submitted_at"));
        return new Account.Identity(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("email"),
                Rows.enumValue(rs, "status", UserStatus.class),
                profile,
                verification,
                Rows.instant(rs, "created_at"));
    }

    private static Professional.Details professional(ResultSet rs) throws SQLException {
        return new Professional.Details(
                rs.getString("pro_display_title"),
                Rows.text(rs, "pro_bio"),
                Rows.integer(rs, "pro_years_of_experience"),
                Rows.bool(rs, "pro_is_accepting_clients", true));
    }
}
