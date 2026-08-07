package com.laxman.codereviewassistant.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laxman.codereviewassistant.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Runs when a request reaches a protected endpoint without valid authentication
 * (missing/expired/invalid JWT). Without this, Spring Security's default handler
 * writes a blank 403 with no body, which the frontend can't parse or explain to
 * the user -- it just shows a generic "Request failed".
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse body = new ErrorResponse(
                "Your session has expired. Please sign in again.", 401, null);

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}