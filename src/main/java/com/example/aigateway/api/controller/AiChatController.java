package com.example.aigateway.api.controller;

import com.example.aigateway.api.request.AiChatRequest;
import com.example.aigateway.api.response.AiChatResponse;
import com.example.aigateway.application.service.AiGatewayService;
import com.example.aigateway.domain.security.GatewayPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiGatewayService aiGatewayService;

    public AiChatController(AiGatewayService aiGatewayService) {
        this.aiGatewayService = aiGatewayService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(
            @Valid @RequestBody AiChatRequest request,
            @AuthenticationPrincipal GatewayPrincipal principal
    ) {
        AiChatResponse response = aiGatewayService.process(request, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @Valid @RequestBody AiChatRequest request,
            @AuthenticationPrincipal GatewayPrincipal principal
    ) {
        return aiGatewayService.stream(request, principal);
    }
}
