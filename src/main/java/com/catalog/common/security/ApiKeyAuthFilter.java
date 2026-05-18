package com.catalog.common.security;

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

    public ApiKeyAuthFilter(
            @Value("${catalog.security.require-api-key:false}") boolean requireApiKey,
            @Value("${catalog.security.api-keys:}") String apiKeysCsv) {
        this.requireApiKey = requireApiKey;
        this.allowedKeys = Arrays.stream(apiKeysCsv.split(","))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .collect(Collectors.toSet());
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
        if (apiKey == null || apiKey.isBlank() || !allowedKeys.contains(apiKey.trim())) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                {"status":401,"error":"Unauthorized",
                 "message":"Missing or invalid API key."}
                """);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isSafeMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }

    private boolean isApiPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/");
    }
}

