package com.example.aigateway.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aigateway.infrastructure.config.GatewayRedisProperties;
import com.example.aigateway.infrastructure.config.GatewaySecurityProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisClientRateLimiterTest {

    @Test
    @DisplayName("Redis rate limiter는 분당 한도를 초과하면 차단 결과를 반환한다")
    void blocksWhenMinuteLimitExceeded() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any(String.class))).thenReturn(3L);

        RedisClientRateLimiter limiter = new RedisClientRateLimiter(
                redisTemplate,
                new GatewayRedisProperties(true, "test-gateway")
        );

        RateLimitResult result = limiter.consume(new GatewaySecurityProperties.ClientProperties(
                "client-1",
                "tenant-1",
                "api-key",
                "OPERATOR",
                2,
                100,
                1000,
                List.of("mock"),
                List.of(),
                null
        ));

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    @DisplayName("Redis rate limiter는 첫 요청에서 TTL을 설정한다")
    void setsExpiryOnFirstRequest() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any(String.class))).thenReturn(1L);

        RedisClientRateLimiter limiter = new RedisClientRateLimiter(
                redisTemplate,
                new GatewayRedisProperties(true, "test-gateway")
        );

        RateLimitResult result = limiter.consume(new GatewaySecurityProperties.ClientProperties(
                "client-2",
                "tenant-1",
                "api-key",
                "OPERATOR",
                5,
                100,
                1000,
                List.of("mock"),
                List.of(),
                null
        ));

        assertThat(result.allowed()).isTrue();
        verify(redisTemplate).expire(any(String.class), any(Duration.class));
    }
}
