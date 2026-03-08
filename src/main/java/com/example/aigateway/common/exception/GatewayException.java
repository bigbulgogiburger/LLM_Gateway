package com.example.aigateway.common.exception;

import org.springframework.http.HttpStatus;

public class GatewayException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public GatewayException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
