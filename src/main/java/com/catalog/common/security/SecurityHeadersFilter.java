package com.catalog.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security response headers.
 * Applied to every HTTP response regardless of outcome.
 *
 * X-Content-Type-Options: prevent MIME-type sniffing
 * X-Frame-Options: prevent clickjacking (DENY = no iframes at all)
 * HSTS: force HTTPS for 1 year including subdomains (prod only; configure via env)
 * Referrer-Policy: don't leak full URL to third parties
 * Content-Security-Policy: this API returns JSON only — 'none' is correct
 *
 * X-XSS-Protection is intentionally ABSENT:
 * Modern browsers removed it. CSP is the correct replacement.
 * Setting X-XSS-Protection: 1; mode=block can actually introduce XSS vulnerabilities.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private final boolean enableHsts;

    public SecurityHeadersFilter(@org.springframework.beans.factory.annotation.Value("${catalog.security.enable-hsts:false}") boolean enableHsts) {
        this.enableHsts = enableHsts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Restrict what this API response can load.
        // JSON APIs don't load scripts, images, or stylesheets.
        response.setHeader("Content-Security-Policy", "default-src 'none'");

        // Permissions-Policy: disable browser features not needed for an API
        response.setHeader("Permissions-Policy", "geolocation=(), camera=(), microphone=()");

        // HSTS: enforce HTTPS. Only set in production (HTTPS must be enabled).
        // In local dev: removed to allow HTTP. Configure via environment.
        if (enableHsts) {
            response.setHeader("Strict-Transport-Security",
                "max-age=31536000; includeSubDomains; preload");
        }

        chain.doFilter(request, response);
    }
}

