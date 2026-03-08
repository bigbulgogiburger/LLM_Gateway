package com.example.aigateway.common.exception;

public final class GatewayErrorCodes {

    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
    public static final String PROVIDER_NOT_ALLOWED = "PROVIDER_NOT_ALLOWED";
    public static final String PROVIDER_EXECUTION_FAILED = "PROVIDER_EXECUTION_FAILED";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String FORBIDDEN = "FORBIDDEN";

    private GatewayErrorCodes() {
    }
}
