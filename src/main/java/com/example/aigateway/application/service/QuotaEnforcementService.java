package com.example.aigateway.application.service;

import com.example.aigateway.application.dto.ProviderUsage;
import com.example.aigateway.application.dto.ProviderResult;
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

    public void recordProviderUsage(GatewayPrincipal principal, String prompt, ProviderResult providerResult) {
        ProviderUsage usage = providerResult == null ? null : providerResult.usage();
        if (usage == null) {
            recordResponseUsage(principal, providerResult == null ? null : providerResult.content());
            return;
        }
        int estimatedPromptTokens = tokenEstimator.estimate(prompt);
        int deltaTokens = usage.totalTokens() - estimatedPromptTokens;
        quotaStore.adjustTokens(principal.clientId(), deltaTokens);
    }
}
