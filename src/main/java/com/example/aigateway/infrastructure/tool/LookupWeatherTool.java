package com.example.aigateway.infrastructure.tool;

import com.example.aigateway.domain.tool.ExecutableTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class LookupWeatherTool implements ExecutableTool {

    private final ObjectMapper objectMapper;

    public LookupWeatherTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "lookup_weather";
    }

    @Override
    public String description() {
        return "현재 날씨를 조회한다";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("city", objectMapper.createObjectNode().put("type", "string"));
        schema.set("properties", properties);
        schema.putArray("required").add("city");
        return schema;
    }

    @Override
    public JsonNode execute(JsonNode arguments) {
        String city = arguments == null ? "unknown" : arguments.path("city").asText("unknown");
        ObjectNode result = objectMapper.createObjectNode();
        result.put("city", city);
        result.put("condition", "sunny");
        result.put("temperatureCelsius", 23);
        result.put("summary", city + " is sunny and 23C.");
        return result;
    }
}
