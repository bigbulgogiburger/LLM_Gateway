package com.example.aigateway.application.service;

import com.example.aigateway.common.exception.GatewayErrorCodes;
import com.example.aigateway.common.exception.GatewayException;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "gateway.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryQuotaStore implements QuotaStore {

    private final ConcurrentMap<String, DailyQuotaState> quotas = new ConcurrentHashMap<>();

    @Override
    public void checkAndConsumeRequest(String clientId, int requestQuota, int tokenQuota, int promptTokens) {
        DailyQuotaState state = quotas.compute(clientId, (key, current) -> {
            LocalDate today = LocalDate.now();
            if (current == null || !current.date().equals(today)) {
                return new DailyQuotaState(today, new AtomicInteger(), new AtomicInteger());
            }
            return current;
        });

        int nextRequestCount = state.requestCount().incrementAndGet();
        if (nextRequestCount > requestQuota) {
            state.requestCount().decrementAndGet();
            throw new GatewayException(GatewayErrorCodes.QUOTA_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS, "일일 요청 quota를 초과했습니다.");
        }

        int nextTokenCount = state.tokenCount().addAndGet(promptTokens);
        if (nextTokenCount > tokenQuota) {
            state.requestCount().decrementAndGet();
            state.tokenCount().addAndGet(-promptTokens);
            throw new GatewayException(GatewayErrorCodes.QUOTA_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS, "일일 토큰 quota를 초과했습니다.");
        }
    }

    @Override
    public void recordResponseTokens(String clientId, int responseTokens) {
        DailyQuotaState state = quotas.get(clientId);
        if (state != null) {
            state.tokenCount().addAndGet(responseTokens);
        }
    }

    @Override
    public void adjustTokens(String clientId, int deltaTokens) {
        DailyQuotaState state = quotas.get(clientId);
        if (state != null && deltaTokens != 0) {
            state.tokenCount().addAndGet(deltaTokens);
        }
    }

    private record DailyQuotaState(LocalDate date, AtomicInteger requestCount, AtomicInteger tokenCount) {
    }
}
