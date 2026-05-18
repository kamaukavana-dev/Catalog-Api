package com.catalog.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(@Value("${catalog.cors.allowed-origins:http://localhost:3000}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        boolean wildcard = java.util.Arrays.stream(allowedOrigins)
                .anyMatch(origin -> "*".equals(origin));

        var registration = registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization",
                                "X-Request-Id", "X-Idempotency-Key", "X-Api-Key")
                .exposedHeaders("X-Request-Id", "Retry-After")
                .maxAge(3600); // Browser caches preflight for 1 hour
        registration.allowCredentials(!wildcard);
    }

    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}
