package com.talkcare.api.error;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
    }
}
