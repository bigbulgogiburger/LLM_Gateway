package com.example.aigateway.infrastructure.security;

import com.example.aigateway.infrastructure.config.GatewaySecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ApiKeyClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyClientRegistry.class);

    private final GatewaySecurityProperties properties;

    public ApiKeyClientRegistry(GatewaySecurityProperties properties) {
        this.properties = properties;
        properties.clients().stream()
                .filter(client -> !StringUtils.hasText(client.apiKeyHash()) && StringUtils.hasText(client.apiKey()))
                .map(GatewaySecurityProperties.ClientProperties::clientId)
                .forEach(this::logPlaintextFallback);
    }

    public String headerName() {
        return properties.apiKeyHeader();
    }

    public Optional<GatewaySecurityProperties.ClientProperties> findByApiKey(String apiKey) {
        return properties.clients().stream()
                .filter(client -> apiKeysMatch(client, apiKey))
                .findFirst();
    }

    public boolean enabled() {
        return properties.enabled();
    }

    private boolean apiKeysMatch(GatewaySecurityProperties.ClientProperties client, String actual) {
        if (actual == null) {
            return false;
        }
        if (StringUtils.hasText(client.apiKeyHash())) {
            return hashMatches(client.apiKeyHash(), actual);
        }
        if (!StringUtils.hasText(client.apiKey())) {
            return false;
        }
        return MessageDigest.isEqual(
                client.apiKey().getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean hashMatches(String expectedHash, String rawKey) {
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                sha256Hex(rawKey).getBytes(StandardCharsets.UTF_8)
        );
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", exception);
        }
    }

    private void logPlaintextFallback(String clientId) {
        log.warn("Client {} still uses plaintext api-key configuration; migrate to api-key-hash.", clientId);
    }
}
