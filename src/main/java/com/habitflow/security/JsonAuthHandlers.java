package com.habitflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Returns JSON (not an HTML redirect) when an /api/** call is unauthenticated or forbidden. */
public final class JsonAuthHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper mapper;

    public JsonAuthHandlers(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(request, response, 401, "Unauthorized", "Please sign in to continue.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, 403, "Forbidden", "You do not have access to this resource.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("path", request.getRequestURI());
        mapper.writeValue(response.getOutputStream(), body);
    }
}
