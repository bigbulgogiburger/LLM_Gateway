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
import com.example.aigateway.api.response.ToolCallView;
import com.example.aigateway.application.dto.AiGatewayCommand;
import com.example.aigateway.application.dto.AiGuardrailAssessment;
import com.example.aigateway.application.dto.ModerationAssessment;
import com.example.aigateway.application.dto.ProviderResult;
import com.example.aigateway.application.dto.ProviderStreamEvent;
import com.example.aigateway.application.dto.ProviderToolCall;
import com.example.aigateway.application.dto.ProviderUsage;
import com.example.aigateway.application.dto.ToolExecutionResult;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
    private final ToolPolicyService toolPolicyService;
    private final ToolExecutionService toolExecutionService;
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
            ToolPolicyService toolPolicyService,
            ToolExecutionService toolExecutionService,
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
        this.toolPolicyService = toolPolicyService;
        this.toolExecutionService = toolExecutionService;
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
        toolPolicyService.validateRequestedTools(principal, request.tools(), request.toolChoice());
        AiGatewayCommand command = new AiGatewayCommand(
                requestIdGenerator.generate(),
                principal.tenantId(),
                principal.clientId(),
                request.userId(),
                principal.role(),
                request.provider(),
                inputText,
                toMessages(request.messages()),
                toResponseFormat(request.responseFormat()),
                Boolean.TRUE.equals(request.stream()),
                toTools(request.tools()),
                toToolChoice(request.toolChoice())
        );

        GuardrailDecision ruleDecision = ruleGuardrailService.evaluate(command);
        if (ruleDecision.isBlocked()) {
            long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
            AiChatResponse response = buildBlockedResponse(command, request.provider(), ruleDecision, null, null, null, null);
            auditLogService.log(toAuditEvent(command, response, elapsedMillis, List.of(), null, null));
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
            auditLogService.log(toAuditEvent(command, response, elapsedMillis, List.of(), null, null));
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
            auditLogService.log(toAuditEvent(command, response, elapsedMillis, List.of(), null, null));
            gatewayMetricsService.recordOutcome(request.provider(), "blocked_ai", command.tenantId());
            gatewayMetricsService.recordLatency(request.provider(), "blocked_ai", command.tenantId(), elapsedMillis);
            return response;
        }

        ProviderExecutionOutcome providerOutcome = executeToolLoop(provider, command, principal);
        ProviderResult providerResult = providerOutcome.result();
        String content = providerResult.content() == null ? "" : providerResult.content();
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
            quotaEnforcementService.recordProviderUsage(principal, inputText, providerResult);
            auditLogService.log(toAuditEvent(command, response, elapsedMillis, providerOutcome.executedToolCalls(), providerResult.model(), providerResult.usage()));
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
            quotaEnforcementService.recordProviderUsage(principal, inputText, providerResult);
            auditLogService.log(toAuditEvent(command, response, elapsedMillis, providerOutcome.executedToolCalls(), providerResult.model(), providerResult.usage()));
            gatewayMetricsService.recordOutcome(provider.name(), "blocked_output", command.tenantId());
            gatewayMetricsService.recordLatency(provider.name(), "blocked_output", command.tenantId(), elapsedMillis);
            return response;
        }

        quotaEnforcementService.recordProviderUsage(principal, inputText, providerResult);
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
                new AiChatData(outputDecision.content(), toToolCallViews(providerOutcome.executedToolCalls()))
        );
        auditLogService.log(toAuditEvent(command, response, elapsedMillis, providerOutcome.executedToolCalls(), providerResult.model(), providerResult.usage()));
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

        SseEmitter emitter = new SseEmitter(30_000L);
        CompletableFuture.runAsync(() -> streamWithProvider(emitter, request, principal, provider));
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

    private AuditEvent toAuditEvent(
            AiGatewayCommand command,
            AiChatResponse response,
            long elapsedMillis,
            List<ProviderToolCall> toolCalls,
            String model,
            ProviderUsage usage
    ) {
        return new AuditEvent(
                command.requestId(),
                command.tenantId(),
                command.clientId(),
                command.userId(),
                command.role(),
                response.provider(),
                model,
                response.status(),
                response.guardrail().passed(),
                response.guardrail().aiResult() == null ? null : response.guardrail().aiResult().verdict(),
                response.guardrail().output() == null || response.guardrail().output().passed(),
                response.guardrail().output() != null && response.guardrail().output().modified(),
                response.guardrail().ruleResults().stream().map(RuleResultView::code).toList(),
                toolCalls == null ? 0 : toolCalls.size(),
                toolCalls == null ? List.of() : toolCalls.stream().map(ProviderToolCall::name).distinct().toList(),
                usage == null ? null : usage.inputTokens(),
                usage == null ? null : usage.outputTokens(),
                usage == null ? null : usage.totalTokens(),
                usage == null ? null : usage.costUsd(),
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

    private void streamWithProvider(SseEmitter emitter, AiChatRequest request, GatewayPrincipal principal, LlmProvider provider) {
        try {
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
            validateProviderCapabilities(provider, request);
            toolPolicyService.validateRequestedTools(principal, request.tools(), request.toolChoice());

            AiGatewayCommand command = new AiGatewayCommand(
                    requestIdGenerator.generate(),
                    principal.tenantId(),
                    principal.clientId(),
                    request.userId(),
                    principal.role(),
                    request.provider(),
                    inputText,
                    toMessages(request.messages()),
                    toResponseFormat(request.responseFormat()),
                    Boolean.TRUE.equals(request.stream()),
                    toTools(request.tools()),
                    toToolChoice(request.toolChoice())
            );

            GuardrailDecision ruleDecision = ruleGuardrailService.evaluate(command);
            if (ruleDecision.isBlocked()) {
                emitBlockedAndComplete(emitter, buildBlockedResponse(command, request.provider(), ruleDecision, null, null, null, null));
                return;
            }

            ModerationAssessment inputModeration = moderationService.assessInput(command);
            if (inputModeration.blocks()) {
                GuardrailDecision blockedDecision = GuardrailDecision.blocked(List.of(new RuleResult(
                        GuardrailResultCode.INPUT_MODERATION_BLOCKED.name(),
                        inputModeration.reason(),
                        inputModeration.verdict().name()
                )));
                emitBlockedAndComplete(emitter, buildBlockedResponse(command, request.provider(), blockedDecision, inputModeration, null, null, null));
                return;
            }

            AiGuardrailAssessment aiAssessment = aiGuardrailService.assess(command);
            if (aiAssessment.blocksRequest()) {
                RuleResult aiRuleResult = new RuleResult(
                        GuardrailResultCode.AI_GUARDRAIL_BLOCKED.name(),
                        aiAssessment.reason(),
                        aiAssessment.verdict() == AiGuardrailVerdict.REVIEW ? "REVIEW" : "BLOCK"
                );
                emitBlockedAndComplete(
                        emitter,
                        buildBlockedResponse(command, request.provider(), GuardrailDecision.blocked(List.of(aiRuleResult)), inputModeration, aiAssessment, null, null)
                );
                return;
            }

            if (request.tools() != null && !request.tools().isEmpty()) {
                ProviderResult initialResult = providerExecutionService.execute(() -> provider.generate(command));
                streamToolLoop(emitter, command, principal, provider, initialResult, startedAt, ruleDecision, inputModeration, aiAssessment);
                return;
            }

            StringBuilder emittedContent = new StringBuilder();
            List<ProviderToolCall> toolCalls = new ArrayList<>();
            AtomicBoolean blocked = new AtomicBoolean(false);
            AtomicReference<AiChatResponse> blockedResponse = new AtomicReference<>();
            AtomicReference<ProviderUsage> usageRef = new AtomicReference<>();
            AtomicReference<String> modelRef = new AtomicReference<>();

            provider.stream(command, event -> handleProviderStreamEvent(
                    emitter,
                    event,
                    command,
                    provider,
                    new StringBuilder(),
                    emittedContent,
                    toolCalls,
                    blocked,
                    blockedResponse,
                    ruleDecision,
                    inputModeration,
                    aiAssessment,
                    usageRef,
                    modelRef,
                    true
            ));

            if (blocked.get()) {
                recordBlockedStreamOutcome(command, provider, startedAt, blockedResponse.get(), toolCalls, modelRef.get(), usageRef.get());
                return;
            }

            quotaEnforcementService.recordProviderUsage(
                    principal,
                    command.prompt(),
                    new ProviderResult(emittedContent.toString(), List.of(), null, modelRef.get(), usageRef.get())
            );
            long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
            AiChatResponse successResponse = new AiChatResponse(
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
                            null,
                            null
                    ),
                    new AiChatData(emittedContent.toString(), toToolCallViews(toolCalls))
            );
            auditLogService.log(toAuditEvent(command, successResponse, elapsedMillis, toolCalls, modelRef.get(), usageRef.get()));
            gatewayMetricsService.recordOutcome(provider.name(), "success_stream", command.tenantId());
            gatewayMetricsService.recordLatency(provider.name(), "success_stream", command.tenantId(), elapsedMillis);
            emitter.complete();
        } catch (Exception exception) {
            emitter.completeWithError(exception);
        }
    }

    private void handleProviderStreamEvent(
            SseEmitter emitter,
            ProviderStreamEvent event,
            AiGatewayCommand command,
            LlmProvider provider,
            StringBuilder rawContent,
            StringBuilder emittedContent,
            List<ProviderToolCall> toolCalls,
            AtomicBoolean blocked,
            AtomicReference<AiChatResponse> blockedResponse,
            GuardrailDecision ruleDecision,
            ModerationAssessment inputModeration,
            AiGuardrailAssessment aiAssessment,
            AtomicReference<ProviderUsage> usageRef,
            AtomicReference<String> modelRef,
            boolean emitDoneEvent
    ) {
        try {
            if ("tool_call".equals(event.type()) && event.toolCall() != null) {
                toolCalls.add(event.toolCall());
                emitter.send(SseEmitter.event()
                        .name("tool_call")
                        .data(new AiChatStreamEvent(
                                "tool_call",
                                command.requestId(),
                                provider.name(),
                                "SUCCESS",
                                null,
                                toToolCallView(event.toolCall())
                        )));
                return;
            }
            if ("text_delta".equals(event.type()) && event.delta() != null) {
                rawContent.append(event.delta());
                ModerationAssessment outputModeration = moderationService.assessOutput(rawContent.toString(), command);
                if (outputModeration.blocks()) {
                    blocked.set(true);
                    GuardrailDecision blockedDecision = GuardrailDecision.blocked(List.of(new RuleResult(
                            GuardrailResultCode.OUTPUT_MODERATION_BLOCKED.name(),
                            outputModeration.reason(),
                            outputModeration.verdict().name()
                    )));
                    AiChatResponse blockedResponseBody = buildBlockedResponse(
                            command,
                            provider.name(),
                            blockedDecision,
                            inputModeration,
                            aiAssessment,
                            outputModeration,
                            null
                    );
                    blockedResponse.set(blockedResponseBody);
                    emitBlockedAndComplete(emitter, blockedResponseBody);
                    return;
                }

                OutputGuardrailDecision outputDecision = outputGuardrailService.evaluate(rawContent.toString(), command);
                if (outputDecision.isBlocked()) {
                    blocked.set(true);
                    AiChatResponse blockedResponseBody = buildBlockedResponse(
                            command,
                            provider.name(),
                            GuardrailDecision.passed(ruleDecision.results()),
                            inputModeration,
                            aiAssessment,
                            outputModeration,
                            outputDecision
                    );
                    blockedResponse.set(blockedResponseBody);
                    emitBlockedAndComplete(emitter, blockedResponseBody);
                    return;
                }

                String safeContent = outputDecision.content();
                String deltaToEmit = deriveStreamDelta(emittedContent.toString(), safeContent);
                emittedContent.setLength(0);
                emittedContent.append(safeContent);
                if (!deltaToEmit.isEmpty()) {
                    emitter.send(SseEmitter.event()
                            .name("chunk")
                            .data(new AiChatStreamEvent(
                                    "chunk",
                                    command.requestId(),
                                    provider.name(),
                                    "SUCCESS",
                                    deltaToEmit,
                                    null
                            )));
                }
                return;
            }
            if ("done".equals(event.type())) {
                if (event.usage() != null) {
                    usageRef.updateAndGet(current -> mergeUsage(current, event.usage()));
                }
                if (event.model() != null && !event.model().isBlank()) {
                    modelRef.set(event.model());
                }
            }
            if (emitDoneEvent && "done".equals(event.type())) {
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(new AiChatStreamEvent(
                                "done",
                                command.requestId(),
                                provider.name(),
                                "SUCCESS",
                                null,
                                null
                        )));
            }
        } catch (Exception exception) {
            blocked.set(true);
            emitter.completeWithError(exception);
        }
    }

    private String deriveStreamDelta(String previousContent, String currentContent) {
        if (currentContent == null) {
            return "";
        }
        if (previousContent == null || previousContent.isEmpty()) {
            return currentContent;
        }
        if (currentContent.startsWith(previousContent)) {
            return currentContent.substring(previousContent.length());
        }
        return currentContent;
    }

    private void emitBlockedAndComplete(SseEmitter emitter, AiChatResponse response) {
        try {
            emitter.send(SseEmitter.event()
                    .name("blocked")
                    .data(new AiChatStreamEvent(
                            "blocked",
                            response.requestId(),
                            response.provider(),
                            response.status(),
                            null,
                            null
                    )));
            emitter.complete();
        } catch (Exception exception) {
            emitter.completeWithError(exception);
        }
    }

    private ProviderExecutionOutcome executeToolLoop(LlmProvider provider, AiGatewayCommand command, GatewayPrincipal principal) {
        ProviderResult result = providerExecutionService.execute(() -> provider.generate(command));
        List<ProviderToolCall> executedToolCalls = new ArrayList<>(result.toolCalls());
        ProviderUsage aggregatedUsage = result.usage();
        int remainingIterations = 3;
        while (!result.toolCalls().isEmpty() && remainingIterations-- > 0) {
            List<ToolExecutionResult> toolResults = toolExecutionService.execute(principal, result.toolCalls());
            ProviderResult previousResult = result;
            result = providerExecutionService.execute(() -> provider.continueWithToolOutputs(command, previousResult, toolResults));
            executedToolCalls.addAll(result.toolCalls());
            aggregatedUsage = mergeUsage(aggregatedUsage, result.usage());
        }
        ensureToolLoopCompleted(result);
        ProviderResult aggregatedResult = new ProviderResult(
                result.content(),
                result.toolCalls(),
                result.responseId(),
                result.model(),
                aggregatedUsage
        );
        return new ProviderExecutionOutcome(aggregatedResult, executedToolCalls);
    }

    private void streamToolLoop(
            SseEmitter emitter,
            AiGatewayCommand command,
            GatewayPrincipal principal,
            LlmProvider provider,
            ProviderResult initialResult,
            Instant startedAt,
            GuardrailDecision ruleDecision,
            ModerationAssessment inputModeration,
            AiGuardrailAssessment aiAssessment
    ) {
        try {
            ProviderResult currentResult = initialResult;
            StringBuilder rawContent = new StringBuilder();
            StringBuilder emittedContent = new StringBuilder();
            List<ProviderToolCall> executedToolCalls = new ArrayList<>();
            AtomicBoolean blocked = new AtomicBoolean(false);
            AtomicReference<AiChatResponse> blockedResponse = new AtomicReference<>();
            AtomicReference<ProviderUsage> usageRef = new AtomicReference<>(initialResult.usage());
            AtomicReference<String> modelRef = new AtomicReference<>(initialResult.model());
            int remainingIterations = 3;
            while (!currentResult.toolCalls().isEmpty() && remainingIterations-- > 0) {
                executedToolCalls.addAll(currentResult.toolCalls());
                currentResult.toolCalls().forEach(toolCall -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("tool_call")
                                .data(new AiChatStreamEvent(
                                        "tool_call",
                                        command.requestId(),
                                        provider.name(),
                                        "SUCCESS",
                                        null,
                                        toToolCallView(toolCall)
                                )));
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
                List<ToolExecutionResult> toolResults = toolExecutionService.execute(principal, currentResult.toolCalls());
                ProviderResult previousResult = currentResult;
                List<ProviderToolCall> nextToolCalls = new ArrayList<>();
                provider.streamWithToolOutputs(command, previousResult, toolResults, event -> handleProviderStreamEvent(
                        emitter,
                        event,
                        command,
                        provider,
                        rawContent,
                        emittedContent,
                        nextToolCalls,
                        blocked,
                        blockedResponse,
                        ruleDecision,
                        inputModeration,
                        aiAssessment,
                        usageRef,
                        modelRef,
                        false
                ));
                if (blocked.get()) {
                    recordBlockedStreamOutcome(command, provider, startedAt, blockedResponse.get(), executedToolCalls, modelRef.get(), usageRef.get());
                    return;
                }
                currentResult = new ProviderResult(
                        emittedContent.toString(),
                        nextToolCalls,
                        previousResult.responseId(),
                        modelRef.get(),
                        usageRef.get()
                );
            }
            if (emittedContent.isEmpty() && currentResult.content() != null && !currentResult.content().isBlank()) {
                rawContent.append(currentResult.content());
                ModerationAssessment outputModeration = moderationService.assessOutput(rawContent.toString(), command);
                if (outputModeration.blocks()) {
                    AiChatResponse blockedResponseBody = buildBlockedResponse(
                            command,
                            provider.name(),
                            GuardrailDecision.blocked(List.of(new RuleResult(
                                    GuardrailResultCode.OUTPUT_MODERATION_BLOCKED.name(),
                                    outputModeration.reason(),
                                    outputModeration.verdict().name()
                            ))),
                            inputModeration,
                            aiAssessment,
                            outputModeration,
                            null
                    );
                    emitBlockedAndComplete(emitter, blockedResponseBody);
                    recordBlockedStreamOutcome(command, provider, startedAt, blockedResponseBody, executedToolCalls, modelRef.get(), usageRef.get());
                    return;
                }
                OutputGuardrailDecision outputDecision = outputGuardrailService.evaluate(rawContent.toString(), command);
                if (outputDecision.isBlocked()) {
                    AiChatResponse blockedResponseBody = buildBlockedResponse(
                            command,
                            provider.name(),
                            GuardrailDecision.passed(ruleDecision.results()),
                            inputModeration,
                            aiAssessment,
                            outputModeration,
                            outputDecision
                    );
                    emitBlockedAndComplete(emitter, blockedResponseBody);
                    recordBlockedStreamOutcome(command, provider, startedAt, blockedResponseBody, executedToolCalls, modelRef.get(), usageRef.get());
                    return;
                }
                emittedContent.append(outputDecision.content());
                emitter.send(SseEmitter.event()
                        .name("chunk")
                        .data(new AiChatStreamEvent(
                                "chunk",
                                command.requestId(),
                                provider.name(),
                                "SUCCESS",
                                outputDecision.content(),
                                null
                        )));
            }
            ensureToolLoopCompleted(currentResult);

            quotaEnforcementService.recordProviderUsage(
                    principal,
                    command.prompt(),
                    new ProviderResult(emittedContent.toString(), List.of(), null, modelRef.get(), usageRef.get())
            );
            long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
            AiChatResponse successResponse = buildStreamSuccessResponse(
                    command,
                    provider.name(),
                    ruleDecision,
                    inputModeration,
                    aiAssessment,
                    emittedContent.toString(),
                    executedToolCalls
            );
            auditLogService.log(toAuditEvent(command, successResponse, elapsedMillis, executedToolCalls, modelRef.get(), usageRef.get()));
            gatewayMetricsService.recordOutcome(provider.name(), "success_stream", command.tenantId());
            gatewayMetricsService.recordLatency(provider.name(), "success_stream", command.tenantId(), elapsedMillis);
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(new AiChatStreamEvent(
                            "done",
                            command.requestId(),
                            provider.name(),
                            "SUCCESS",
                            null,
                            null
                    )));
            emitter.complete();
        } catch (Exception exception) {
            emitter.completeWithError(exception);
        }
    }

    private AiChatResponse buildStreamSuccessResponse(
            AiGatewayCommand command,
            String providerName,
            GuardrailDecision ruleDecision,
            ModerationAssessment inputModeration,
            AiGuardrailAssessment aiAssessment,
            String content,
            List<ProviderToolCall> toolCalls
    ) {
        return new AiChatResponse(
                "SUCCESS",
                command.requestId(),
                command.tenantId(),
                command.clientId(),
                providerName,
                new GuardrailView(
                        true,
                        ruleDecision.results().stream().map(this::toView).toList(),
                        toModerationView(inputModeration),
                        toAiView(aiAssessment),
                        null,
                        null
                ),
                new AiChatData(content, toToolCallViews(toolCalls))
        );
    }

    private void recordBlockedStreamOutcome(
            AiGatewayCommand command,
            LlmProvider provider,
            Instant startedAt,
            AiChatResponse response,
            List<ProviderToolCall> toolCalls,
            String model,
            ProviderUsage usage
    ) {
        if (response == null) {
            return;
        }
        long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
        auditLogService.log(toAuditEvent(command, response, elapsedMillis, toolCalls, model, usage));
        String outcome = response.guardrail().ruleResults().stream()
                .map(RuleResultView::code)
                .filter(code -> code != null && !code.isBlank())
                .findFirst()
                .map(this::toStreamBlockedOutcome)
                .orElse("blocked_stream");
        gatewayMetricsService.recordOutcome(provider.name(), outcome, command.tenantId());
        gatewayMetricsService.recordLatency(provider.name(), outcome, command.tenantId(), elapsedMillis);
    }

    private String toStreamBlockedOutcome(String ruleCode) {
        return switch (ruleCode) {
            case "OUTPUT_MODERATION_BLOCKED" -> "blocked_output_moderation_stream";
            case "AI_GUARDRAIL_BLOCKED" -> "blocked_ai_stream";
            default -> "blocked_stream";
        };
    }

    private void ensureToolLoopCompleted(ProviderResult result) {
        if (!result.toolCalls().isEmpty()) {
            throw new GatewayException(
                    GatewayErrorCodes.PROVIDER_EXECUTION_FAILED,
                    HttpStatus.BAD_GATEWAY,
                    "tool 호출 반복 한도를 초과했습니다."
            );
        }
    }

    private ProviderUsage mergeUsage(ProviderUsage left, ProviderUsage right) {
        if (left == null) {
            return right;
        }
        return left.add(right);
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
        if (Boolean.TRUE.equals(request.stream()) && !provider.capabilities().supportsStreaming()) {
            throw new GatewayException(
                    GatewayErrorCodes.PROVIDER_CAPABILITY_NOT_SUPPORTED,
                    HttpStatus.BAD_REQUEST,
                    "선택한 provider는 streaming을 지원하지 않습니다."
            );
        }
        if (request.tools() != null && !request.tools().isEmpty() && !provider.capabilities().supportsToolUse()) {
            throw new GatewayException(
                    GatewayErrorCodes.PROVIDER_CAPABILITY_NOT_SUPPORTED,
                    HttpStatus.BAD_REQUEST,
                    "선택한 provider는 tool use를 지원하지 않습니다."
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

    private List<AiGatewayCommand.ToolDefinition> toTools(List<AiChatRequest.ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream()
                .map(tool -> new AiGatewayCommand.ToolDefinition(
                        tool.type(),
                        tool.name(),
                        tool.description(),
                        tool.inputSchema()
                ))
                .toList();
    }

    private AiGatewayCommand.ToolChoice toToolChoice(AiChatRequest.ToolChoice toolChoice) {
        if (toolChoice == null) {
            return null;
        }
        return new AiGatewayCommand.ToolChoice(toolChoice.type(), toolChoice.name());
    }

    private List<ToolCallView> toToolCallViews(List<ProviderToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        return toolCalls.stream().map(this::toToolCallView).toList();
    }

    private ToolCallView toToolCallView(ProviderToolCall toolCall) {
        return new ToolCallView(
                toolCall.id(),
                toolCall.callId(),
                toolCall.name(),
                toolCall.arguments()
        );
    }

    private record ProviderExecutionOutcome(
            ProviderResult result,
            List<ProviderToolCall> executedToolCalls
    ) {
    }
}
