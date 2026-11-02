package com.talkcare.api.web.dto;

import com.talkcare.api.domain.ServiceType;
import com.talkcare.api.domain.lookup.ConcernType;
import com.talkcare.api.domain.lookup.IdType;
import com.talkcare.api.domain.lookup.ServiceTypeInfo;
import java.util.List;

/** Reference data for the desktop forms. */
public record LookupsResponse(List<ServiceItem> serviceTypes, List<ConcernItem> concernTypes, List<String> idTypes) {

    public static LookupsResponse of(List<ServiceTypeInfo> services, List<ConcernType> concerns, List<IdType> idTypes) {
        return new LookupsResponse(
                services.stream().map(s -> new ServiceItem(s.code(), s.displayName())).toList(),
                concerns.stream().map(c -> new ConcernItem(c.name(), c.serviceType())).toList(),
                idTypes.stream().map(IdType::name).toList());
    }

    public record ServiceItem(ServiceType code, String displayName) {
    }

    public record ConcernItem(String name, ServiceType serviceType) {
    }
}
