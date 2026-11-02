package com.talkcare.api.domain.activity;

import java.time.Instant;

/** An {@code activity_log} row, with the actor's name resolved. */
public record ActivityEntry(
        String action,
        String details,
        String entityType,
        int entityId,
        String actorName,
        Instant createdAt) {
}
