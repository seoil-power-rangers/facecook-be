package com.facecook.common.exception;

import lombok.Getter;

/**
 * "요청은 정상적으로 처리됐지만 규칙상 거절한다"를 알리는 예외. 서비스가
 * {@code throw new ApiException(ErrorCode.DUPLICATE)}처럼 던지면
 * {@link GlobalExceptionHandler#handleApiException}이 받아서
 * {@code ErrorCode}의 HTTP 상태와 {@code {"code","message"}} JSON으로 바꿔 응답한다.
 *
 * <p>{@link RuntimeException}(unchecked)을 상속한 이유: 호출하는 메서드마다
 * {@code throws}를 적지 않아도 되고, {@code @Transactional}은 기본적으로
 * unchecked 예외가 나면 롤백한다 — 서비스에서 이 예외를 던지면 그 트랜잭션에서
 * 쓴 DB 변경도 함께 취소된다.</p>
 *
 * <p>WebSocket(STOMP)에서 던지면 이 핸들러가 아니라
 * {@code ChatStompErrorHandler}가 ERROR 프레임으로 바꾼다.</p>
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
