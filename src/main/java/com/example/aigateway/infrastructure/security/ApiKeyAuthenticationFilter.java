package com.example.aigateway.infrastructure.security;

import com.example.aigateway.common.GatewayRequestAttributes;
import com.example.aigateway.common.exception.GatewayErrorCodes;
import com.example.aigateway.domain.security.GatewayPrincipal;
import com.example.aigateway.infrastructure.config.GatewaySecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyClientRegistry clientRegistry;
    private final ClientRateLimiter clientRateLimiter;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(
            ApiKeyClientRegistry clientRegistry,
            ClientRateLimiter clientRateLimiter,
            ObjectMapper objectMapper
    ) {
        this.clientRegistry = clientRegistry;
        this.clientRateLimiter = clientRateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!clientRegistry.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(clientRegistry.headerName());
        GatewaySecurityProperties.ClientProperties client = apiKey == null
                ? null
                : clientRegistry.findByApiKey(apiKey).orElse(null);

        if (client == null) {
            writeError(response, request, HttpServletResponse.SC_UNAUTHORIZED, GatewayErrorCodes.UNAUTHORIZED,
                    "유효한 API Key가 필요합니다.");
            return;
        }

        RateLimitResult result = clientRateLimiter.consume(client);
        if (!result.allowed()) {
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            writeError(response, request, 429, GatewayErrorCodes.RATE_LIMITED, "요청 한도를 초과했습니다.");
            return;
        }

        GatewayPrincipal principal = new GatewayPrincipal(
                client.clientId(),
                client.tenantId(),
                client.role(),
                client.dailyRequestQuota(),
                client.dailyTokenQuota(),
                client.allowedProviders()
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + client.role()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), new ErrorBody(
                Instant.now(),
                status,
                code,
                message,
                (String) request.getAttribute(GatewayRequestAttributes.REQUEST_ID),
                request.getRequestURI()
        ));
    }

    private record ErrorBody(
            Instant timestamp,
            int status,
            String code,
            String message,
            String requestId,
            String path
    ) {
    }
}
