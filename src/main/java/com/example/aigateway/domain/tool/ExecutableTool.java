package com.example.aigateway.domain.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Duration;
import java.util.List;

public interface ExecutableTool {
    String name();

    default String description() {
        return name();
    }

    default JsonNode inputSchema() {
        return JsonNodeFactory.instance.objectNode();
    }

    JsonNode execute(JsonNode arguments);

    default Duration timeout() {
        return Duration.ofSeconds(2);
    }

    default List<String> allowedRoles() {
        return List.of();
    }

    default List<String> allowedClients() {
        return List.of();
    }

    default List<String> allowedTenants() {
        return List.of();
    }

    default boolean isRetryable(Throwable throwable) {
        return false;
    }
}
