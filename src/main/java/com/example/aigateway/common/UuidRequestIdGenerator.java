package com.example.aigateway.common;

import java.util.UUID;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.stereotype.Component;

@Component
public class UuidRequestIdGenerator implements RequestIdGenerator {

    @Override
    public String generate() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            Object requestId = requestAttributes.getAttribute(GatewayRequestAttributes.REQUEST_ID, RequestAttributes.SCOPE_REQUEST);
            if (requestId instanceof String requestIdValue) {
                return requestIdValue;
            }
        }
        return UUID.randomUUID().toString();
    }
}
