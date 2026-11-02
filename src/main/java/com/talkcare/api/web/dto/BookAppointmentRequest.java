package com.talkcare.api.web.dto;

import jakarta.validation.constraints.Size;

/** {@code start} is a local date-time in the app time zone ({@code 2026-11-05T14:00}). */
public record BookAppointmentRequest(
        String serviceType,
        String start,
        Integer durationMinutes,
        @Size(max = 1000, message = Inputs.TOO_LONG) String notes) {

    public BookAppointmentRequest {
        notes = Inputs.trimToNull(notes);
    }
}
