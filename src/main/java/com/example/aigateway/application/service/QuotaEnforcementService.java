package com.example.aigateway.application.service;

import com.example.aigateway.domain.security.GatewayPrincipal;
import org.springframework.stereotype.Service;

@Service
public class QuotaEnforcementService {

    private final TokenEstimator tokenEstimator;
    private final QuotaStore quotaStore;

    public QuotaEnforcementService(TokenEstimator tokenEstimator, QuotaStore quotaStore) {
        this.tokenEstimator = tokenEstimator;
        this.quotaStore = quotaStore;
    }

    public void checkAndConsumeRequest(GatewayPrincipal principal, String prompt) {
        int promptTokens = tokenEstimator.estimate(prompt);
        quotaStore.checkAndConsumeRequest(principal.clientId(), principal.dailyRequestQuota(), principal.dailyTokenQuota(), promptTokens);
    }

    public void recordResponseUsage(GatewayPrincipal principal, String content) {
        quotaStore.recordResponseTokens(principal.clientId(), tokenEstimator.estimate(content));
    }
}
