package com.talkcare.api.error;

import org.springframework.http.HttpStatus;

public class PayloadTooLargeException extends ApiException {

    public PayloadTooLargeException() {
        this("The upload is too large.");
    }

    public PayloadTooLargeException(String message) {
        super(message, HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE");
    }
}
