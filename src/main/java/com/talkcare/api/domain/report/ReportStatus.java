package com.talkcare.api.domain.report;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** {@code reports.status} with the label shown in the UI. */
public enum ReportStatus {
    UNDER_REVIEW("Under Review"),
    IN_PROGRESS("In Progress"),
    RESOLVED("Resolved"),
    REJECTED("Rejected");

    private final String label;

    ReportStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Accepts "Under Review", "under_review", "IN-PROGRESS", ... */
    public static Optional<ReportStatus> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String code = value.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        return Arrays.stream(values()).filter(status -> status.name().equals(code)).findFirst();
    }
}
