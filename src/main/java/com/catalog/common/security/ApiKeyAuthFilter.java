package com.catalog.common.security;

import com.catalog.common.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Minimal API key authentication for mutation endpoints.
 *
 * Enforced only when catalog.security.require-api-key is true.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Api-Key";

    private final boolean requireApiKey;
    private final Set<String> allowedKeys;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(
            @Value("${catalog.security.require-api-key:false}") boolean requireApiKey,
            @Value("${catalog.security.api-keys:}") String apiKeysCsv,
            ObjectMapper objectMapper) {
        this.requireApiKey = requireApiKey;
        this.allowedKeys = Arrays.stream(apiKeysCsv.split(","))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .collect(Collectors.toSet());
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!requireApiKey || isSafeMethod(request.getMethod()) || !isApiPath(request)) {
            chain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);
        boolean isValid = false;

        if (apiKey != null && !apiKey.isBlank()) {
            byte[] apiKeyBytes = apiKey.trim().getBytes(StandardCharsets.UTF_8);
            for (String allowedKey : allowedKeys) {
                byte[] allowedKeyBytes = allowedKey.getBytes(StandardCharsets.UTF_8);
                if (MessageDigest.isEqual(apiKeyBytes, allowedKeyBytes)) {
                    isValid = true;
                }
            }
        }

        if (!isValid) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            writeErrorResponse(request, response,
                    HttpStatus.UNAUTHORIZED.value(),
                    "Unauthorized",
                    "Missing or invalid API key.");
            return;
        }

        chain.doFilter(request, response);
    }

    private void writeErrorResponse(HttpServletRequest request,
                                    HttpServletResponse response,
                                    int status,
                                    String error,
                                    String message) throws IOException {
        ErrorResponse body = ErrorResponse.builder()
                .status(status)
                .error(error)
                .message(message)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private boolean isSafeMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }

    private boolean isApiPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/");
    }
}
