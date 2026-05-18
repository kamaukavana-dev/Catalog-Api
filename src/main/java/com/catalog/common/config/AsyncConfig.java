package com.catalog.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-event-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // CRITICAL: Copy MDC context to async threads.
        // Without this, every @Async method loses traceId, requestId, spanId.
        // Log correlation across async boundaries requires this.
        executor.setTaskDecorator(runnable -> {
            Map<String, String> mdcContext = org.slf4j.MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (mdcContext != null) {
                        org.slf4j.MDC.setContextMap(mdcContext);
                    }
                    runnable.run();
                } finally {
                    org.slf4j.MDC.clear();
                }
            };
        });

        executor.initialize();
        return executor;
    }

    // Without this, exceptions in @Async methods are silently swallowed.
    // This is a production trap that burns engineers constantly.
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) ->
            log.error("Unhandled exception in async method '{}': {}",
                      method.getName(), ex.getMessage(), ex);
    }
}
