package com.example.aigateway.infrastructure.security;

import com.example.aigateway.infrastructure.config.GatewayRedisProperties;
import com.example.aigateway.infrastructure.config.GatewaySecurityProperties;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "gateway.redis", name = "enabled", havingValue = "true")
public class RedisClientRateLimiter implements ClientRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final GatewayRedisProperties redisProperties;

    public RedisClientRateLimiter(StringRedisTemplate redisTemplate, GatewayRedisProperties redisProperties) {
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
    }

    @Override
    public RateLimitResult consume(GatewaySecurityProperties.ClientProperties client) {
        long epochMinute = Instant.now().getEpochSecond() / 60;
        String key = redisProperties.namespace() + ":rate:" + client.clientId() + ":" + epochMinute;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofMinutes(2));
        }
        if (count != null && count > client.requestsPerMinute()) {
            return RateLimitResult.blocked(60 - (Instant.now().getEpochSecond() % 60));
        }
        return RateLimitResult.permit();
    }
}
