package com.example.aigateway.infrastructure.security;

public record RateLimitResult(
        boolean allowed,
        long retryAfterSeconds
) {
    public static RateLimitResult permit() {
        return new RateLimitResult(true, 0);
    }

    public static RateLimitResult blocked(long retryAfterSeconds) {
        return new RateLimitResult(false, retryAfterSeconds);
    }
}
