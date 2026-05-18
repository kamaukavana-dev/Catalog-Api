package com.catalog.common.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    /**
     * Enables @Observed annotation processing.
     * Without this bean, @Observed annotations are ignored silently.
     */
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    /**
     * Enables @Timed annotation on arbitrary methods.
     * Distinct from @Observed — TimedAspect creates only Timer metrics, no spans.
     * Use @Observed for service methods (metrics + tracing).
     * Use @Timed for utility methods where tracing overhead isn't needed.
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}

