package com.example.aigateway.application.service;

import com.example.aigateway.api.request.AiChatRequest;
import com.example.aigateway.api.request.AiChatRequest.Message;
import com.example.aigateway.api.response.AiChatData;
import com.example.aigateway.api.response.AiChatResponse;
import com.example.aigateway.api.response.AiGuardrailView;
import com.example.aigateway.api.response.AiChatStreamEvent;
import com.example.aigateway.api.response.GuardrailView;
import com.example.aigateway.api.response.ModerationView;
import com.example.aigateway.api.response.OutputGuardrailView;
import com.example.aigateway.api.response.RuleResultView;
import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.application.dto.AiGuardrailAssessment;
import com.example.aigateway.application.dto.ModerationAssessment;
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
import com.example.aigateway.domain.guardrail.service.ModerationService;
import com.example.aigateway.domain.guardrail.service.OutputGuardrailService;
import com.example.aigateway.domain.guardrail.service.RuleGuardrailService;
import com.example.aigateway.domain.provider.LlmProvider;
import com.example.aigateway.domain.provider.ProviderRouter;
import com.example.aigateway.domain.security.GatewayPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AiGatewayService {

    private final RuleGuardrailService ruleGuardrailService;
    private final AiGuardrailService aiGuardrailService;
    private final ModerationService moderationService;
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
            ModerationService moderationService,
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
        this.moderationService = moderationService;
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
        String inputText = resolveInputText(request);
        quotaEnforcementService.checkAndConsumeRequest(principal, inputText);
        LlmProvider provider = providerRouter.route(request.provider());
        validateProviderCapabilities(provider, request);
        AiGatewayCommand command = new AiGatewayCommand(
                requestIdGenerator.generate(),
                principal.tenantId(),
                principal.clientId(),
                request.userId(),
                principal.role(),
                request.provider(),
                inputText,
                toMessages(request.messages()),
                toResponseFormat(request.responseFormat())
        );

        GuardrailDecision ruleDecision = ruleGuardrailService.evaluate(command);
        if (ruleDecision.isBlocked()) {
            long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
            AiChatResponse response = buildBlockedResponse(command, request.provider(), ruleDecision, null, null, null, null);
            auditLogService.log(toAuditEvent(command, response, elapsedMillis));
            gatewayMetricsService.recordOutcome(request.provider(), "blocked_rule", command.tenantId());
            gatewayMetricsService.recordLatency(request.provider(), "blocked_rule", command.tenantId(), elapsedMillis);
            return response;
        }

        ModerationAssessment inputModeration = moderationService.assessInput(command);
        if (inputModeration.blocks()) {
            GuardrailDecision blockedDecision = GuardrailDecision.blocked(List.of(new RuleResult(
                    GuardrailResultCode.INPUT_MODERATION_BLOCKED.name(),
                    inputModeration.reason(),
                    inputModeration.verdict().name()
            )));
            long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
            AiChatResponse response = buildBlockedResponse(command, request.provider(), blockedDecision, inputModeration, null, null, null);
            auditLogService.log(toAuditEvent(command, response, elapsedMillis));
            gatewayMetricsService.recordOutcome(request.provider(), "blocked_input_moderation", command.tenantId());
            gatewayMetricsService.recordLatency(request.provider(), "blocked_input_moderation", command.tenantId(), elapsedMillis);
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
            AiChatResponse response = buildBlockedResponse(command, request.provider(), blockedDecision, inputModeration, aiAssessment, null, null);
            auditLogService.log(toAuditEvent(command, response, elapsedMillis));
            gatewayMetricsService.recordOutcome(request.provider(), "blocked_ai", command.tenantId());
            gatewayMetricsService.recordLatency(request.provider(), "blocked_ai", command.tenantId(), elapsedMillis);
            return response;
        }

        String content = providerExecutionService.execute(() -> provider.generate(command));
        ModerationAssessment outputModeration = moderationService.assessOutput(content, command);
        if (outputModeration.blocks()) {
            GuardrailDecision blockedDecision = GuardrailDecision.blocked(List.of(new RuleResult(
                    GuardrailResultCode.OUTPUT_MODERATION_BLOCKED.name(),
                    outputModeration.reason(),
                    outputModeration.verdict().name()
            )));
            long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
            AiChatResponse response = buildBlockedResponse(
                    command,
                    provider.name(),
                    blockedDecision,
                    inputModeration,
                    aiAssessment,
                    outputModeration,
                    null
            );
            quotaEnforcementService.recordResponseUsage(principal, content);
            auditLogService.log(toAuditEvent(command, response, elapsedMillis));
            gatewayMetricsService.recordOutcome(provider.name(), "blocked_output_moderation", command.tenantId());
            gatewayMetricsService.recordLatency(provider.name(), "blocked_output_moderation", command.tenantId(), elapsedMillis);
            return response;
        }
        OutputGuardrailDecision outputDecision = outputGuardrailService.evaluate(content, command);
        if (outputDecision.isBlocked()) {
            long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
            AiChatResponse response = buildBlockedResponse(
                    command,
                    provider.name(),
                    GuardrailDecision.passed(ruleDecision.results()),
                    inputModeration,
                    aiAssessment,
                    outputModeration,
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
                        toModerationView(inputModeration),
                        toAiView(aiAssessment),
                        toModerationView(outputModeration),
                        toOutputView(outputDecision)
                ),
                new AiChatData(outputDecision.content())
        );
        auditLogService.log(toAuditEvent(command, response, elapsedMillis));
        gatewayMetricsService.recordOutcome(provider.name(), "success", command.tenantId());
        gatewayMetricsService.recordLatency(provider.name(), "success", command.tenantId(), elapsedMillis);
        return response;
    }

    public SseEmitter stream(AiChatRequest request, GatewayPrincipal principal) {
        LlmProvider provider = providerRouter.route(request.provider());
        if (!provider.capabilities().supportsStreaming()) {
            throw new GatewayException(
                    GatewayErrorCodes.PROVIDER_CAPABILITY_NOT_SUPPORTED,
                    HttpStatus.BAD_REQUEST,
                    "선택한 provider는 streaming을 지원하지 않습니다."
            );
        }

        AiChatResponse response = process(request, principal);
        SseEmitter emitter = new SseEmitter(30_000L);
        CompletableFuture.runAsync(() -> emitStream(emitter, response));
        return emitter;
    }

    private AiChatResponse buildBlockedResponse(
            AiGatewayCommand command,
            String provider,
            GuardrailDecision decision,
            ModerationAssessment inputModeration,
            AiGuardrailAssessment aiAssessment,
            ModerationAssessment outputModeration,
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
                        toModerationView(inputModeration),
                        toAiView(aiAssessment),
                        toModerationView(outputModeration),
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

    private ModerationView toModerationView(ModerationAssessment assessment) {
        if (assessment == null || assessment.isSafe()) {
            return null;
        }
        return new ModerationView(
                assessment.phase(),
                assessment.category(),
                assessment.verdict().name(),
                assessment.reason(),
                assessment.score()
        );
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

    private void emitStream(SseEmitter emitter, AiChatResponse response) {
        try {
            if (!"SUCCESS".equals(response.status()) || response.data() == null) {
                emitter.send(SseEmitter.event()
                        .name("blocked")
                        .data(new AiChatStreamEvent(
                                "blocked",
                                response.requestId(),
                                response.provider(),
                                response.status(),
                                null
                        )));
                emitter.complete();
                return;
            }

            for (String chunk : chunkContent(response.data().content(), 48)) {
                emitter.send(SseEmitter.event()
                        .name("chunk")
                        .data(new AiChatStreamEvent(
                                "chunk",
                                response.requestId(),
                                response.provider(),
                                response.status(),
                                chunk
                        )));
            }
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(new AiChatStreamEvent(
                            "done",
                            response.requestId(),
                            response.provider(),
                            response.status(),
                            null
                    )));
            emitter.complete();
        } catch (Exception exception) {
            emitter.completeWithError(exception);
        }
    }

    private List<String> chunkContent(String content, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }
        for (int start = 0; start < content.length(); start += chunkSize) {
            int end = Math.min(content.length(), start + chunkSize);
            chunks.add(content.substring(start, end));
        }
        return chunks;
    }

    private void validateProviderCapabilities(LlmProvider provider, AiChatRequest request) {
        if (request.messages() != null && !request.messages().isEmpty() && !provider.capabilities().supportsMessages()) {
            throw new GatewayException(
                    GatewayErrorCodes.PROVIDER_CAPABILITY_NOT_SUPPORTED,
                    HttpStatus.BAD_REQUEST,
                    "선택한 provider는 messages 기반 요청을 지원하지 않습니다."
            );
        }
        if (request.responseFormat() != null && !provider.capabilities().supportsStructuredOutputs()) {
            throw new GatewayException(
                    GatewayErrorCodes.PROVIDER_CAPABILITY_NOT_SUPPORTED,
                    HttpStatus.BAD_REQUEST,
                    "선택한 provider는 structured output을 지원하지 않습니다."
            );
        }
    }

    private String resolveInputText(AiChatRequest request) {
        if (request.prompt() != null && !request.prompt().isBlank()) {
            return request.prompt();
        }
        return request.messages().stream()
                .map(Message::content)
                .collect(Collectors.joining("\n"));
    }

    private List<AiGatewayCommand.Message> toMessages(List<AiChatRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .map(message -> new AiGatewayCommand.Message(message.role(), message.content()))
                .toList();
    }

    private AiGatewayCommand.ResponseFormat toResponseFormat(AiChatRequest.ResponseFormat responseFormat) {
        if (responseFormat == null) {
            return null;
        }
        return new AiGatewayCommand.ResponseFormat(
                responseFormat.type(),
                responseFormat.schemaName(),
                responseFormat.strict(),
                responseFormat.schema()
        );
    }
}
