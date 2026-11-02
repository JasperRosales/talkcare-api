package com.talkcare.api.domain;

/**
 * Permission codes from the {@code permissions} table. They are granted to roles through
 * {@code role_permissions} and become Spring Security authorities of the signed-in account.
 * Kept as compile-time constants so they can be used inside {@code @PreAuthorize}.
 */
public final class Permission {

    public static final String REPORTS_CREATE = "reports.create";
    public static final String REPORTS_READ_OWN = "reports.read.own";
    public static final String REPORTS_READ_ASSIGNED = "reports.read.assigned";
    public static final String REPORTS_READ_ALL = "reports.read.all";
    public static final String REPORTS_UPDATE_STATUS = "reports.update.status";
    public static final String CONVERSATIONS_CREATE = "conversations.create";
    public static final String CONVERSATIONS_READ_OWN = "conversations.read.own";
    public static final String CONVERSATIONS_READ_ASSIGNED = "conversations.read.assigned";
    public static final String MESSAGES_SEND = "messages.send";
    public static final String MESSAGES_REPLY = "messages.reply";
    public static final String APPOINTMENTS_BOOK_OWN = "appointments.book.own";
    public static final String APPOINTMENTS_READ_ASSIGNED = "appointments.read.assigned";
    public static final String APPOINTMENTS_MANAGE_ASSIGNED = "appointments.manage.assigned";
    public static final String USERS_READ = "users.read";
    public static final String USERS_MANAGE = "users.manage";
    public static final String VERIFICATIONS_SUBMIT = "verifications.submit";
    public static final String VERIFICATIONS_REVIEW = "verifications.review";
    public static final String ADMIN_DASHBOARD = "admin.dashboard";
    public static final String ADMIN_ACTIVITY_READ = "admin.activity.read";

    private Permission() {
    }
}
