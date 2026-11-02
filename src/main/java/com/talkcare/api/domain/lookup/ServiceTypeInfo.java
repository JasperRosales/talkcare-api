package com.talkcare.api.domain.lookup;

import com.talkcare.api.domain.ServiceType;

/** A {@code service_types} row. */
public record ServiceTypeInfo(int id, ServiceType code, String displayName) {
}
