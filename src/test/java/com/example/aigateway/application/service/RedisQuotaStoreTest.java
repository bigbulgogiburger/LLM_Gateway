package com.example.aigateway.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aigateway.common.exception.GatewayException;
import com.example.aigateway.infrastructure.config.GatewayRedisProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisQuotaStoreTest {

    @Test
    @DisplayName("Redis quota store는 요청 quota 초과 시 예외를 던지고 카운터를 되돌린다")
    void rejectsRequestQuotaExceeded() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any(String.class))).thenReturn(11L);

        RedisQuotaStore quotaStore = new RedisQuotaStore(
                redisTemplate,
                new GatewayRedisProperties(true, "test-gateway")
        );

        assertThatThrownBy(() -> quotaStore.checkAndConsumeRequest("client-1", 10, 1000, 100))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("일일 요청 quota");

        verify(valueOperations).decrement(any(String.class));
    }

    @Test
    @DisplayName("Redis quota store는 토큰 quota 초과 시 요청 카운터와 토큰 카운터를 되돌린다")
    void rejectsTokenQuotaExceeded() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any(String.class))).thenReturn(1L);
        when(valueOperations.increment(any(String.class), anyLong())).thenReturn(1100L);

        RedisQuotaStore quotaStore = new RedisQuotaStore(
                redisTemplate,
                new GatewayRedisProperties(true, "test-gateway")
        );

        assertThatThrownBy(() -> quotaStore.checkAndConsumeRequest("client-1", 10, 1000, 100))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("일일 토큰 quota");

        verify(valueOperations).decrement(any(String.class));
        verify(valueOperations).increment(any(String.class), Mockito.eq(-100L));
    }
}
