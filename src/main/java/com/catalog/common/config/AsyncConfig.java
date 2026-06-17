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
        // Since virtual threads are enabled, we can use a simple task executor
        // that delegates to a virtual thread per task executor.
        executor.setCorePoolSize(0); // Not relevant for virtual threads but required by some implementations
        executor.setThreadNamePrefix("async-vthread-");
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

        // Use Java 21 Virtual Thread Executor
        executor.setVirtualThreads(true);
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
