package com.talkcare.api.domain;

/** {@code users.status}. */
public enum UserStatus {
    ACTIVE,
    PENDING_VERIFICATION,
    VERIFIED,
    SUSPENDED,
    DEACTIVATED;

    /** Suspended and deactivated accounts cannot sign in or use an existing session. */
    public boolean canSignIn() {
        return this != SUSPENDED && this != DEACTIVATED;
    }
}
