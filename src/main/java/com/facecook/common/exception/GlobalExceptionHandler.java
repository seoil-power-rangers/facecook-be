package com.facecook.common.exception;

import com.facecook.common.session.SessionAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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
     * 파라미터 타입이 안 맞거나(?limit=abc) 필수 파라미터가 빠진 요청은 클라이언트
     * 잘못이다. 아래 catch-all이 잡으면 500이 돼서 서버 장애처럼 보인다.
     */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequestParameter() {
        return ResponseEntity.badRequest().body(ErrorResponse.from(ErrorCode.VALIDATION));
    }

    /**
     * 지원하지 않는 HTTP 메서드(405)·미디어 타입(415)은 Spring이 정한 상태와
     * 헤더(Allow, Accept)를 그대로 돌려준다. 클라이언트가 고칠 방법을 알려주는
     * 헤더라서 400으로 뭉개지 않는다. 본문은 정적 리소스 404처럼 비운다.
     */
    @ExceptionHandler({
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<Void> handleUnsupportedRequest(org.springframework.web.ErrorResponse exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .headers(exception.getHeaders())
                .build();
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
