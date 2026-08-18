package com.inovexx.order_service.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meterRegistry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(10)
                .recordExceptions(
                        io.grpc.StatusRuntimeException.class,
                        java.io.IOException.class,
                        java.util.concurrent.TimeoutException.class
                )
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry)
                .bindTo(meterRegistry);
        return registry;
    }

    @Bean
    public RetryRegistry retryRegistry() {
        // Создаем функцию экспоненциальной задержки: 100мс базовая, множитель 2
        IntervalFunction intervalWithExponentialBackoff = IntervalFunction
                .ofExponentialBackoff(Duration.ofMillis(100), 2.0);

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(intervalWithExponentialBackoff) // Задает backoff
                .retryExceptions(
                        io.grpc.StatusRuntimeException.class,
                        java.io.IOException.class,
                        java.util.concurrent.TimeoutException.class
                )
                .build();
        return RetryRegistry.of(config);
    }
}
