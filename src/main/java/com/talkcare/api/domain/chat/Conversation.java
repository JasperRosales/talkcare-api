package com.talkcare.api.domain.chat;

import com.talkcare.api.domain.ServiceType;
import java.time.Instant;

/** A client ↔ professional conversation for one service. */
public record Conversation(
        int id,
        int clientId,
        String clientName,
        int professionalId,
        String professionalName,
        String professionalTitle,
        String professionalType,
        ServiceType serviceType,
        String title,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static final String OPEN = "OPEN";

    public boolean isOpen() {
        return OPEN.equals(status);
    }

    public boolean belongsToClient(int userId) {
        return clientId == userId;
    }
}
