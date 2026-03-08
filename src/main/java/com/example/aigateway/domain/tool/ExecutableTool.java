package com.example.aigateway.domain.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Duration;

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
}
