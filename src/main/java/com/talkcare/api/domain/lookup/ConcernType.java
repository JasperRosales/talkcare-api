package com.talkcare.api.domain.lookup;

import com.talkcare.api.domain.ServiceType;

/** A {@code concern_types} row; {@code serviceType} is null for concerns any service can handle. */
public record ConcernType(int id, String name, ServiceType serviceType) {
}
