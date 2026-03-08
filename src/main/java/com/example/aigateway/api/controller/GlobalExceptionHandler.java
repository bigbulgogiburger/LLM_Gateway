package com.example.aigateway.api.controller;

import com.example.aigateway.api.response.ApiErrorResponse;
import com.example.aigateway.common.GatewayRequestAttributes;
import com.example.aigateway.common.exception.GatewayErrorCodes;
import com.example.aigateway.common.exception.GatewayException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ApiErrorResponse> handleGatewayException(GatewayException exception, HttpServletRequest request) {
        return build(exception.status(), exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, GatewayErrorCodes.VALIDATION_ERROR, exception.getMessage(), request);
    }

    @ExceptionHandler({IllegalStateException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiErrorResponse> handleUpstreamOrValidation(Exception exception, HttpServletRequest request) {
        HttpStatus status = exception instanceof MethodArgumentNotValidException
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.BAD_GATEWAY;
        String code = exception instanceof MethodArgumentNotValidException
                ? GatewayErrorCodes.VALIDATION_ERROR
                : GatewayErrorCodes.PROVIDER_EXECUTION_FAILED;
        return build(status, code, exception.getMessage(), request);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                (String) request.getAttribute(GatewayRequestAttributes.REQUEST_ID),
                request.getRequestURI()
        ));
    }
}
