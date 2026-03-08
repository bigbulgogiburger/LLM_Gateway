package com.example.aigateway.infrastructure.tool;

import com.example.aigateway.domain.tool.ExecutableTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class LookupTimeTool implements ExecutableTool {

    private final ObjectMapper objectMapper;

    public LookupTimeTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "lookup_time";
    }

    @Override
    public String description() {
        return "현재 시간을 조회한다";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("timezone", objectMapper.createObjectNode().put("type", "string"));
        schema.set("properties", properties);
        schema.putArray("required").add("timezone");
        return schema;
    }

    @Override
    public JsonNode execute(JsonNode arguments) {
        String timezone = arguments == null ? "UTC" : arguments.path("timezone").asText("UTC");
        ObjectNode result = objectMapper.createObjectNode();
        result.put("timezone", timezone);
        result.put("currentTime", "2026-03-08T12:00:00");
        return result;
    }
}
