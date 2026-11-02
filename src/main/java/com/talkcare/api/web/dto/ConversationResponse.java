package com.talkcare.api.web.dto;

import com.talkcare.api.domain.ServiceType;
import com.talkcare.api.domain.chat.Conversation;
import java.time.Instant;

public record ConversationResponse(
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

    public static ConversationResponse from(Conversation c) {
        return new ConversationResponse(
                c.id(), c.clientId(), c.clientName(), c.professionalId(), c.professionalName(), c.professionalTitle(),
                c.professionalType(), c.serviceType(), c.title(), c.status(), c.createdAt(), c.updatedAt());
    }
}
