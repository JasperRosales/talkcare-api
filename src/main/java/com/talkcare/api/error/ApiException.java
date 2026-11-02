package com.talkcare.api.error;

import org.springframework.http.HttpStatus;

/** Base application error carrying an HTTP status and a stable machine-readable code. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
