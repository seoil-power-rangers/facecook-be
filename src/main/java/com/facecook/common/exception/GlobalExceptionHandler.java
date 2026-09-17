package com.facecook.common.exception;

import com.facecook.common.session.SessionAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * ApiException(409/403/429 등 정상적인 비즈니스 응답)은 지금까지 아무 로그도
     * 안 남겼다 — 부하테스트 중 4xx가 몰려도 어떤 에러코드가 어느 API에서 났는지
     * 나중에 로그로 확인할 방법이 없었다. WARN으로 남겨서 grep 가능하게 한다.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(
            ApiException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn(
                "{} {} - {} userId={}: {}",
                request.getMethod(),
                request.getRequestURI(),
                errorCode.name(),
                SessionAuthenticationInterceptor.currentUserId(request),
                exception.getMessage()
        );
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.name(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse(ErrorCode.VALIDATION.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.validation(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse(ErrorCode.VALIDATION.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.validation(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage() {
        return ResponseEntity.badRequest().body(ErrorResponse.from(ErrorCode.VALIDATION));
    }

    /**
     * 존재하지 않는 정적 리소스 요청은 정상적인 404 상황이다 — 아래
     * catch-all(Exception) 핸들러가 이걸 500으로 잡아버리면, 실제 장애가
     * 아닌데도 ALB 5xx 알람이 울린다(부하테스트 중 실측으로 확인함).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Unhandled server exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.from(ErrorCode.INTERNAL_ERROR));
    }
}
