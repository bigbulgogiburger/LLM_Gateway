package com.example.aigateway.api.response;

import java.time.Instant;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String requestId,
        String path
) {
}
