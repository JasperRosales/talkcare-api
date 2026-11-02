package com.talkcare.api.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.talkcare.api.domain.activity.Notification;
import java.time.Instant;

/** Snake-case field names, as the Express API returned the raw rows. */
public record NotificationResponse(
        int id,
        String type,
        String message,
        @JsonProperty("is_read") boolean read,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("read_at") Instant readAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.id(), n.type(), n.message(), n.read(), n.createdAt(), n.readAt());
    }
}
