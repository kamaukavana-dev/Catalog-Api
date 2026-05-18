package com.catalog.common.observability.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates MDC with request-scoped context for every HTTP request.
 *
 * Fields populated here appear in EVERY log statement during that request's
 * lifecycle, including logs from downstream services, async callbacks, etc.
 * (Async threads need explicit MDC copy — see @Async configuration.)
 *
 * traceId/spanId: populated automatically by Micrometer Tracing — not here.
 * requestId: unique per HTTP request, not per trace (traces can span multiple requests).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcRequestFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_HTTP_METHOD = "httpMethod";
    private static final String MDC_REQUEST_URI = "requestUri";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            // Use caller-provided request ID if present (API gateway, load balancer);
            // otherwise generate one. This enables request correlation across services.
            String requestId = request.getHeader(REQUEST_ID_HEADER);
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }

            MDC.put(MDC_REQUEST_ID, requestId);
            MDC.put(MDC_HTTP_METHOD, request.getMethod());
            MDC.put(MDC_REQUEST_URI, sanitizeUri(request.getRequestURI()));

            // Propagate request ID back in the response header
            // so clients can correlate their request ID with our logs
            response.setHeader(REQUEST_ID_HEADER, requestId);

            // Phase Security: add userId from SecurityContextHolder here
            // MDC.put("userId", SecurityContextHolder.getContext()
            //     .getAuthentication().getName());

            filterChain.doFilter(request, response);

        } finally {
            // CRITICAL: Always clear MDC after request completes.
            // Thread pool reuse means stale MDC fields leak into subsequent requests.
            MDC.clear();
        }
    }

    /**
     * Remove UUIDs and numeric IDs from URI for MDC to prevent high cardinality.
     * /api/v1/products/550e8400-e29b-41d4-a716-446655440000 → /api/v1/products/{id}
     *
     * High-cardinality MDC fields don't cause the same problems as high-cardinality
     * metric tags, but they do bloat log index size in ELK/CloudWatch.
     */
    private String sanitizeUri(String uri) {
        return uri.replaceAll(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            "{id}"
        );
    }
}

