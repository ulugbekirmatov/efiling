package com.irs.mef.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for Spring Retry to handle IRS MeF session limits and transient errors.
 *
 * Implements exponential backoff strategy for retrying failed operations:
 * - Attempt 1: Immediate
 * - Attempt 2: After 5 seconds
 * - Attempt 3: After 15 seconds (5s * 3.0 multiplier)
 * - Attempt 4: After 45 seconds (15s * 3.0 multiplier, capped at 60s max)
 */
@Slf4j
@Configuration
public class RetryConfig {

    /**
     * Creates a RetryTemplate bean configured for IRS MeF operations.
     *
     * This template handles:
     * - Session limit errors from IRS (concurrent session restrictions)
     * - Connection timeouts and transient network issues
     * - ServiceException errors that may be temporary
     *
     * @return configured RetryTemplate with exponential backoff
     */
    @Bean
    public RetryTemplate mefRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Configure exponential backoff policy
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(5000L);      // Start with 5 seconds
        backOffPolicy.setMultiplier(3.0);              // Triple the wait time each retry
        backOffPolicy.setMaxInterval(60000L);          // Cap at 60 seconds maximum
        retryTemplate.setBackOffPolicy(backOffPolicy);

        // Configure retry policy - retry on any Exception (will be filtered in services)
        // We use a simple approach: retry up to 3 times for any exception
        // Individual services will decide if specific exceptions are retryable
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(Exception.class, true);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
            3,                          // Maximum 3 attempts (1 initial + 2 retries)
            retryableExceptions,
            true                        // Traverse exception causes
        );
        retryTemplate.setRetryPolicy(retryPolicy);

        // Register retry listener for logging
        retryTemplate.registerListener(new RetryListener() {
            @Override
            public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
                // Called before first attempt - no logging needed
                return true;
            }

            @Override
            public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                if (throwable != null && context.getRetryCount() > 0) {
                    log.warn("All {} retry attempts exhausted for operation. Last error: {}",
                        context.getRetryCount(), throwable.getMessage());
                }
            }

            @Override
            public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                String errorMsg = throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();

                // Check if this is a session limit error
                if (errorMsg.contains("Session limit") ||
                    errorMsg.contains("Too many concurrent sessions") ||
                    errorMsg.contains("concurrent session")) {
                    log.warn("IRS session limit reached (attempt {}/3). Will retry after exponential backoff...",
                        context.getRetryCount() + 1);
                } else if (errorMsg.contains("timeout") || errorMsg.contains("timed out")) {
                    log.warn("Connection timeout occurred (attempt {}/3). Will retry after exponential backoff...",
                        context.getRetryCount() + 1);
                } else {
                    log.warn("Operation failed (attempt {}/3): {}. Will retry after exponential backoff...",
                        context.getRetryCount() + 1, errorMsg);
                }
            }
        });

        return retryTemplate;
    }
}
