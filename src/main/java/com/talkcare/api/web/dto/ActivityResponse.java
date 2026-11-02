package com.talkcare.api.web.dto;

import com.talkcare.api.domain.activity.ActivityEntry;
import java.time.Instant;

public record ActivityResponse(String action, String details, String entityType, int entityId, String actorName, Instant createdAt) {

    public static ActivityResponse from(ActivityEntry a) {
        return new ActivityResponse(a.action(), a.details(), a.entityType(), a.entityId(), a.actorName(), a.createdAt());
    }
}
