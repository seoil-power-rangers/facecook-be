package com.facecook.common.exception;

/**
 * 오류 응답 본문 {@code {"code": "...", "message": "..."}}. REST 오류는
 * {@link GlobalExceptionHandler}가, STOMP 오류는 {@code ChatStompErrorHandler}가
 * 같은 모양으로 만든다 — FE가 한 가지 형식만 해석하면 된다.
 *
 * <p>{@code record}를 쓴 이유: 만들어진 뒤 바뀔 일이 없는 값 묶음이라
 * 생성자·getter·equals를 직접 쓰지 않아도 되고, Jackson이 필드 이름 그대로
 * JSON으로 바꿔 준다.</p>
 */
public record ErrorResponse(String code, String message) {

    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage());
    }

    public static ErrorResponse validation(String message) {
        return new ErrorResponse(ErrorCode.VALIDATION.name(), message);
    }
}
