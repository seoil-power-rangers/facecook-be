package com.facecook.common.exception;

public record ErrorResponse(String code, String message) {

    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage());
    }

    public static ErrorResponse validation(String message) {
        return new ErrorResponse(ErrorCode.VALIDATION.name(), message);
    }
}
