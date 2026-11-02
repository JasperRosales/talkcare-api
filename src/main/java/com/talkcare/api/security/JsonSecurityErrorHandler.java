package com.talkcare.api.security;

import com.talkcare.api.error.ApiException;
import com.talkcare.api.error.ErrorResponse;
import com.talkcare.api.error.ForbiddenException;
import com.talkcare.api.error.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes Spring Security's 401/403 outcomes in the API's {@code { error, code }} format. */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper json;

    public JsonSecurityErrorHandler(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        Object failure = request.getAttribute(SessionAuthenticationFilter.FAILURE_ATTRIBUTE);
        write(response, failure instanceof UnauthenticatedException ex ? ex : new UnauthenticatedException());
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        write(response, new ForbiddenException());
    }

    private void write(HttpServletResponse response, ApiException error) throws IOException {
        response.setStatus(error.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), ErrorResponse.of(error));
    }
}
