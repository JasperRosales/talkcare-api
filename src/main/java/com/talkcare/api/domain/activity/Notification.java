package com.talkcare.api.domain.activity;

import java.time.Instant;

/** A {@code notifications} row. */
public record Notification(int id, String type, String message, boolean read, Instant createdAt, Instant readAt) {

    public static final String SYSTEM = "SYSTEM";
    public static final String REPORT = "REPORT";
    public static final String VERIFICATION = "VERIFICATION";
}
