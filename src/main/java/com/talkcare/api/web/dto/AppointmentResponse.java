package com.talkcare.api.web.dto;

import com.talkcare.api.domain.ServiceType;
import com.talkcare.api.domain.appointment.Appointment;
import com.talkcare.api.domain.appointment.AppointmentStatus;
import java.time.Instant;

public record AppointmentResponse(
        int id,
        int clientId,
        String clientName,
        int professionalId,
        String professionalName,
        ServiceType serviceType,
        Instant start,
        Instant end,
        AppointmentStatus status,
        String statusLabel,
        String notes,
        Instant createdAt) {

    public static AppointmentResponse from(Appointment a) {
        return new AppointmentResponse(a.id(), a.clientId(), a.clientName(), a.professionalId(), a.professionalName(),
                a.serviceType(), a.start(), a.end(), a.status(), a.status().label(), a.notes(), a.createdAt());
    }
}
