package com.talkcare.api.domain.appointment;

import com.talkcare.api.domain.ServiceType;
import java.time.Instant;

/** A session between a client and the professional assigned to them for a service. */
public record Appointment(
        int id,
        int clientId,
        String clientName,
        int professionalId,
        String professionalName,
        ServiceType serviceType,
        Instant start,
        Instant end,
        AppointmentStatus status,
        String notes,
        Instant createdAt) {
}
