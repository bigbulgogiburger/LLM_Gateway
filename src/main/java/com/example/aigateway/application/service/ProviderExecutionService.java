package com.example.aigateway.application.service;

import com.example.aigateway.common.exception.GatewayErrorCodes;
import com.example.aigateway.common.exception.GatewayException;
import com.example.aigateway.infrastructure.config.ProviderResilienceProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.concurrent.Callable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProviderExecutionService {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ProviderExecutionService(ProviderResilienceProperties properties) {
        this.circuitBreaker = CircuitBreaker.of("providerExecution", CircuitBreakerConfig.custom()
                .slidingWindowSize(properties.circuitBreakerSlidingWindowSize())
                .minimumNumberOfCalls(properties.circuitBreakerMinimumCalls())
                .failureRateThreshold(properties.circuitBreakerFailureRateThreshold())
                .waitDurationInOpenState(properties.circuitBreakerOpenDuration())
                .build());
        this.retry = Retry.of("providerExecution", RetryConfig.custom()
                .maxAttempts(properties.retryAttempts())
                .waitDuration(properties.retryBackoff())
                .build());
    }

    public <T> T execute(Callable<T> providerCall) {
        Callable<T> decorated = CircuitBreaker.decorateCallable(circuitBreaker, providerCall);
        decorated = Retry.decorateCallable(retry, decorated);
        try {
            return decorated.call();
        } catch (CallNotPermittedException exception) {
            throw new GatewayException(
                    GatewayErrorCodes.PROVIDER_EXECUTION_FAILED,
                    HttpStatus.BAD_GATEWAY,
                    "현재 LLM provider가 일시적으로 차단되어 있습니다."
            );
        } catch (GatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GatewayException(
                    GatewayErrorCodes.PROVIDER_EXECUTION_FAILED,
                    HttpStatus.BAD_GATEWAY,
                    "LLM provider 호출에 실패했습니다."
            );
        }
    }
}
