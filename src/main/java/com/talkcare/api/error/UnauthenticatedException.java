package com.talkcare.api.error;

import org.springframework.http.HttpStatus;

public class UnauthenticatedException extends ApiException {

    public UnauthenticatedException() {
        this("Please sign in to continue.");
    }

    public UnauthenticatedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }
}
