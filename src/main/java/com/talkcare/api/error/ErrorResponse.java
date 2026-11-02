package com.talkcare.api.error;

/** Body of every error response: {@code { "error": "<message>", "code": "<CODE>" }}. */
public record ErrorResponse(String error, String code) {

    public static ErrorResponse of(ApiException exception) {
        return new ErrorResponse(exception.getMessage(), exception.code());
    }
}
