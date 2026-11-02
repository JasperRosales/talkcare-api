package com.talkcare.api.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Turns every exception into the API's {@code { error, code }} body. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final DatabaseErrorTranslator databaseErrors;

    public GlobalExceptionHandler(DatabaseErrorTranslator databaseErrors) {
        this.databaseErrors = databaseErrors;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(ErrorResponse.of(ex));
    }

    /** Bean Validation failures on request bodies; the first message is reported. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(ObjectError::getDefaultMessage)
                .findFirst()
                .orElse("The request is invalid.");
        return handleApi(new ValidationException(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof PayloadTooLargeException tooLarge) {
                return handleApi(tooLarge);
            }
        }
        return handleApi(new ValidationException("The request body is not valid JSON."));
    }

    /** Raised by {@code @PreAuthorize} when the account lacks the permission. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return handleApi(new ForbiddenException());
    }

    @ExceptionHandler({NoHandlerFoundException.class, HttpRequestMethodNotSupportedException.class})
    ResponseEntity<ErrorResponse> handleUnknownEndpoint(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                "Unknown endpoint: " + request.getMethod() + " " + request.getRequestURI(), "NOT_FOUND"));
    }

    /** Missing file under /uploads. */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleMissingUpload() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Not found.", "HTTP_ERROR"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return databaseErrors.translate(ex)
                .map(t -> ResponseEntity.status(t.status()).body(t.body()))
                .orElseGet(() -> {
                    log.error("{} {} failed", request.getMethod(), request.getRequestURI(), ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                            new ErrorResponse("Something went wrong on the TalkCare server.", "INTERNAL_ERROR"));
                });
    }
}
