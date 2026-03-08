package com.example.aigateway.application.service;

import com.example.aigateway.common.exception.GatewayErrorCodes;
import com.example.aigateway.common.exception.GatewayException;
import com.example.aigateway.infrastructure.config.GatewayRedisProperties;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "gateway.redis", name = "enabled", havingValue = "true")
public class RedisQuotaStore implements QuotaStore {

    private final StringRedisTemplate redisTemplate;
    private final GatewayRedisProperties redisProperties;

    public RedisQuotaStore(StringRedisTemplate redisTemplate, GatewayRedisProperties redisProperties) {
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
    }

    @Override
    public void checkAndConsumeRequest(String clientId, int requestQuota, int tokenQuota, int promptTokens) {
        LocalDate today = LocalDate.now();
        String requestKey = redisProperties.namespace() + ":quota:req:" + clientId + ":" + today;
        String tokenKey = redisProperties.namespace() + ":quota:token:" + clientId + ":" + today;
        Duration ttl = Duration.between(LocalDateTime.now(), LocalDateTime.of(today.plusDays(1), LocalTime.MIDNIGHT));

        Long requestCount = redisTemplate.opsForValue().increment(requestKey);
        if (requestCount != null && requestCount == 1L) {
            redisTemplate.expire(requestKey, ttl);
        }
        if (requestCount != null && requestCount > requestQuota) {
            redisTemplate.opsForValue().decrement(requestKey);
            throw new GatewayException(GatewayErrorCodes.QUOTA_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS, "일일 요청 quota를 초과했습니다.");
        }

        Long tokenCount = redisTemplate.opsForValue().increment(tokenKey, promptTokens);
        if (tokenCount != null && tokenCount.equals((long) promptTokens)) {
            redisTemplate.expire(tokenKey, ttl);
        }
        if (tokenCount != null && tokenCount > tokenQuota) {
            redisTemplate.opsForValue().decrement(requestKey);
            redisTemplate.opsForValue().increment(tokenKey, -promptTokens);
            throw new GatewayException(GatewayErrorCodes.QUOTA_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS, "일일 토큰 quota를 초과했습니다.");
        }
    }

    @Override
    public void recordResponseTokens(String clientId, int responseTokens) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        String tokenKey = redisProperties.namespace() + ":quota:token:" + clientId + ":" + today;
        redisTemplate.opsForValue().increment(tokenKey, responseTokens);
    }

    @Override
    public void adjustTokens(String clientId, int deltaTokens) {
        if (deltaTokens == 0) {
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        String tokenKey = redisProperties.namespace() + ":quota:token:" + clientId + ":" + today;
        redisTemplate.opsForValue().increment(tokenKey, deltaTokens);
    }
}
