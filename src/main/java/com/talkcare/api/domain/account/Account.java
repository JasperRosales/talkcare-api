package com.talkcare.api.domain.account;

import com.talkcare.api.domain.Role;
import com.talkcare.api.domain.UserStatus;
import com.talkcare.api.domain.chat.Conversation;
import com.talkcare.api.domain.report.Report;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Account hierarchy, mirroring the class-table inheritance in docs/schema.md:
 *
 * <pre>
 *   Account (abstract)            users + user_profiles
 *   ├── Client                    clients
 *   ├── Administrator             admins
 *   └── Professional (abstract)   professionals
 *       ├── Attorney              attorneys
 *       └── Psychiatrist          psychiatrists
 * </pre>
 *
 * Authorization is permission-based ({@link #can}) plus polymorphic resource scoping
 * ({@link #canAccessConversation}, {@link #canAccessReport}), so callers never compare role strings.
 */
public abstract sealed class Account permits Client, Professional, Administrator {

    private final Identity identity;
    private final Set<String> permissions;

    protected Account(Identity identity, Set<String> permissions) {
        this.identity = identity;
        this.permissions = Collections.unmodifiableSet(new TreeSet<>(permissions));
    }

    public abstract Role role();

    /** Which screen the desktop UI opens after sign-in. */
    public abstract String dashboardView();

    public abstract boolean canAccessConversation(Conversation conversation);

    public abstract boolean canAccessReport(Report report);

    public String roleDisplayName() {
        String code = role().name();
        return code.charAt(0) + code.substring(1).toLowerCase();
    }

    public final boolean can(String permission) {
        return permissions.contains(permission);
    }

    public final boolean isActive() {
        return identity.status().canSignIn();
    }

    public final String displayName() {
        String fullName = identity.profile().fullName();
        return fullName == null || fullName.isEmpty() ? identity.username() : fullName;
    }

    public final int id() {
        return identity.id();
    }

    public final String username() {
        return identity.username();
    }

    public final String email() {
        return identity.email();
    }

    public final UserStatus status() {
        return identity.status();
    }

    public final Profile profile() {
        return identity.profile();
    }

    /** Latest identity verification, or {@code null} if none was submitted. */
    public final Verification verification() {
        return identity.verification();
    }

    public final Instant createdAt() {
        return identity.createdAt();
    }

    /** Sorted, read-only. */
    public final Set<String> permissions() {
        return permissions;
    }

    /** Columns of {@code users}, {@code user_profiles} and the latest verification, shared by every subtype. */
    public record Identity(
            int id,
            String username,
            String email,
            UserStatus status,
            Profile profile,
            Verification verification,
            Instant createdAt) {
    }

    public record Profile(String fullName, String contactNo, String address, LocalDate birthDate, String profilePictureUrl) {
    }

    public record Verification(String status, String idType, String idNumber, String rejectionReason, Instant submittedAt) {
    }
}
