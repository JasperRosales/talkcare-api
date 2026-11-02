package com.talkcare.api.domain.appointment;

/** {@code appointments.status}. */
public enum AppointmentStatus {
    SCHEDULED("Requested"),
    CONFIRMED("Confirmed"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    NO_SHOW("No-show");

    private final String label;

    AppointmentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Requested and confirmed sessions can still change; the others are final. */
    public boolean isActive() {
        return this == SCHEDULED || this == CONFIRMED;
    }
}
