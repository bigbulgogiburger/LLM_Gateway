package com.example.aigateway.application.service;

import com.example.aigateway.api.request.AiChatRequest;
import com.example.aigateway.api.response.AiChatData;
import com.example.aigateway.api.response.AiChatResponse;
import com.example.aigateway.api.response.AiGuardrailView;
import com.example.aigateway.api.response.GuardrailView;
import com.example.aigateway.api.response.OutputGuardrailView;
import com.example.aigateway.api.response.RuleResultView;
import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.application.dto.AiGuardrailAssessment;
import com.example.aigateway.common.RequestIdGenerator;
import com.example.aigateway.common.exception.GatewayErrorCodes;
import com.example.aigateway.common.exception.GatewayException;
import com.example.aigateway.domain.audit.AuditLogService;
import com.example.aigateway.domain.audit.AuditEvent;
import com.example.aigateway.domain.guardrail.result.AiGuardrailVerdict;
import com.example.aigateway.domain.guardrail.result.GuardrailDecision;
import com.example.aigateway.domain.guardrail.result.GuardrailResultCode;
import com.example.aigateway.domain.guardrail.result.OutputGuardrailDecision;
import com.example.aigateway.domain.guardrail.result.RuleResult;
import com.example.aigateway.domain.guardrail.service.AiGuardrailService;
import com.example.aigateway.domain.guardrail.service.OutputGuardrailService;
import com.example.aigateway.domain.guardrail.service.RuleGuardrailService;
import com.example.aigateway.domain.provider.LlmProvider;
import com.example.aigateway.domain.provider.ProviderRouter;
import com.example.aigateway.domain.security.GatewayPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AiGatewayService {

    private final RuleGuardrailService ruleGuardrailService;
    private final AiGuardrailService aiGuardrailService;
    private final OutputGuardrailService outputGuardrailService;
    private final ProviderExecutionService providerExecutionService;
    private final QuotaEnforcementService quotaEnforcementService;
    private final GatewayMetricsService gatewayMetricsService;
    private final ProviderRouter providerRouter;
    private final AuditLogService auditLogService;
    private final RequestIdGenerator requestIdGenerator;

    public AiGatewayService(
            RuleGuardrailService ruleGuardrailService,
            AiGuardrailService aiGuardrailService,
            OutputGuardrailService outputGuardrailService,
            ProviderExecutionService providerExecutionService,
            QuotaEnforcementService quotaEnforcementService,
            GatewayMetricsService gatewayMetricsService,
            ProviderRouter providerRouter,
            AuditLogService auditLogService,
            RequestIdGenerator requestIdGenerator
    ) {
        this.ruleGuardrailService = ruleGuardrailService;
        this.aiGuardrailService = aiGuardrailService;
        this.outputGuardrailService = outputGuardrailService;
        this.providerExecutionService = providerExecutionService;
        this.quotaEnforcementService = quotaEnforcementService;
        this.gatewayMetricsService = gatewayMetricsService;
        this.providerRouter = providerRouter;
        this.auditLogService = auditLogService;
        this.requestIdGenerator = requestIdGenerator;
    }

    public AiChatResponse process(AiChatRequest request, GatewayPrincipal principal) {
        Instant startedAt = Instant.now();
        if (!principal.allowsProvider(request.provider())) {
            throw new GatewayException(
                    GatewayErrorCodes.PROVIDER_NOT_ALLOWED,
                    HttpStatus.FORBIDDEN,
                    "현재 API Key로는 요청한 provider를 사용할 수 없습니다."
            );
        }
        quotaEnforcementService.checkAndConsumeRequest(principal, request.prompt());
        AiGatewayCommand command = new AiGatewayCommand(
                requestIdGenerator.generate(),
                principal.tenantId(),
                principal.clientId(),
                request.userId(),
                principal.role(),
                request.provider(),
                request.prompt()
        );

        GuardrailDecision ruleDecision = ruleGuardrailService.evaluate(command);
        if (ruleDecision.isBlocked()) {
            long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
            AiChatResponse response = buildBlockedResponse(command, request.provider(), ruleDecision, null, null);
            auditLogService.log(toAuditEvent(command, response, elapsedMillis));
            gatewayMetricsService.recordOutcome(request.provider(), "blocked_rule", command.tenantId());
            gatewayMetricsService.recordLatency(request.provider(), "blocked_rule", command.tenantId(), elapsedMillis);
            return response;
        }

        AiGuardrailAssessment aiAssessment = aiGuardrailService.assess(command);
        if (aiAssessment.blocksRequest()) {
            RuleResult aiRuleResult = new RuleResult(
                    GuardrailResultCode.AI_GUARDRAIL_BLOCKED.name(),
                    aiAssessment.reason(),
                    aiAssessment.verdict() == AiGuardrailVerdict.REVIEW ? "REVIEW" : "BLOCK"
            );
            GuardrailDecision blockedDecision = GuardrailDecision.blocked(List.of(aiRuleResult));
            long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
            AiChatResponse response = buildBlockedResponse(command, request.provider(), blockedDecision, aiAssessment, null);
            auditLogService.log(toAuditEvent(command, response, elapsedMillis));
            gatewayMetricsService.recordOutcome(request.provider(), "blocked_ai", command.tenantId());
            gatewayMetricsService.recordLatency(request.provider(), "blocked_ai", command.tenantId(), elapsedMillis);
            return response;
        }

        LlmProvider provider = providerRouter.route(command.provider());
        String content = providerExecutionService.execute(() -> provider.generate(command));
        OutputGuardrailDecision outputDecision = outputGuardrailService.evaluate(content, command);
        if (outputDecision.isBlocked()) {
            long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
            AiChatResponse response = buildBlockedResponse(
                    command,
                    provider.name(),
                    GuardrailDecision.passed(ruleDecision.results()),
                    aiAssessment,
                    outputDecision
            );
            quotaEnforcementService.recordResponseUsage(principal, content);
            auditLogService.log(toAuditEvent(command, response, elapsedMillis));
            gatewayMetricsService.recordOutcome(provider.name(), "blocked_output", command.tenantId());
            gatewayMetricsService.recordLatency(provider.name(), "blocked_output", command.tenantId(), elapsedMillis);
            return response;
        }

        quotaEnforcementService.recordResponseUsage(principal, outputDecision.content());
        long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
        AiChatResponse response = new AiChatResponse(
                "SUCCESS",
                command.requestId(),
                command.tenantId(),
                command.clientId(),
                provider.name(),
                new GuardrailView(
                        true,
                        ruleDecision.results().stream().map(this::toView).toList(),
                        toAiView(aiAssessment),
                        toOutputView(outputDecision)
                ),
                new AiChatData(outputDecision.content())
        );
        auditLogService.log(toAuditEvent(command, response, elapsedMillis));
        gatewayMetricsService.recordOutcome(provider.name(), "success", command.tenantId());
        gatewayMetricsService.recordLatency(provider.name(), "success", command.tenantId(), elapsedMillis);
        return response;
    }

    private AiChatResponse buildBlockedResponse(
            AiGatewayCommand command,
            String provider,
            GuardrailDecision decision,
            AiGuardrailAssessment aiAssessment,
            OutputGuardrailDecision outputDecision
    ) {
        return new AiChatResponse(
                "BLOCKED",
                command.requestId(),
                command.tenantId(),
                command.clientId(),
                provider,
                new GuardrailView(
                        false,
                        decision.results().stream().map(this::toView).toList(),
                        toAiView(aiAssessment),
                        toOutputView(outputDecision)
                ),
                null
        );
    }

    private RuleResultView toView(RuleResult result) {
        return new RuleResultView(result.code(), result.reason(), result.action());
    }

    private AiGuardrailView toAiView(AiGuardrailAssessment assessment) {
        if (assessment == null) {
            return null;
        }
        return new AiGuardrailView(assessment.verdict().name(), assessment.reason(), assessment.score());
    }

    private OutputGuardrailView toOutputView(OutputGuardrailDecision decision) {
        if (decision == null) {
            return null;
        }
        return new OutputGuardrailView(
                decision.passed(),
                decision.modified(),
                decision.results().stream().map(this::toView).toList()
        );
    }

    private AuditEvent toAuditEvent(AiGatewayCommand command, AiChatResponse response, long elapsedMillis) {
        return new AuditEvent(
                command.requestId(),
                command.tenantId(),
                command.clientId(),
                command.userId(),
                command.role(),
                response.provider(),
                response.status(),
                response.guardrail().passed(),
                response.guardrail().aiResult() == null ? null : response.guardrail().aiResult().verdict(),
                response.guardrail().output() == null || response.guardrail().output().passed(),
                response.guardrail().output() != null && response.guardrail().output().modified(),
                response.guardrail().ruleResults().stream().map(RuleResultView::code).toList(),
                elapsedMillis,
                summarize(command.prompt())
        );
    }

    private String summarize(String prompt) {
        String sanitized = prompt
                .replaceAll("\\b\\d{6}-\\d{7}\\b", "[RRN]")
                .replaceAll("\\b[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", "[EMAIL]")
                .replaceAll("\\b01[0-9]-?\\d{3,4}-?\\d{4}\\b", "[PHONE]");
        if (sanitized.length() <= 40) {
            return sanitized;
        }
        return sanitized.substring(0, 40) + "...";
    }
}
