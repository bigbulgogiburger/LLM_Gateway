package com.example.aigateway.application.service;

public interface QuotaStore {
    void checkAndConsumeRequest(String clientId, int requestQuota, int tokenQuota, int promptTokens);

    void recordResponseTokens(String clientId, int responseTokens);

    void adjustTokens(String clientId, int deltaTokens);
}
