package com.facecook.chat.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.common.exception.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * STOMP 처리 실패를 {@code {code, message}} JSON body의 ERROR frame으로 변환한다.
 *
 * 이 클래스가 {@link org.springframework.web.socket.messaging.StompSubProtocolErrorHandler}를
 * 상속해서 처리하는 건 프레임 디코딩 같은 "프로토콜 레벨" 오류뿐이다.
 * {@code @MessageMapping} 핸들러(예: {@code ChatMessageController.send})가 던지는
 * 비즈니스 예외(CLOSED, FORBIDDEN 등)는 별도 스레드(inboundChannel executor)에서
 * {@link org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler}가
 * 처리하는데, 여기서 던진 예외는 이 클래스를 절대 거치지 않고 조용히 로그만 찍히고
 * 사라진다 — 그래서 {@code ChatMessageController#handleException}({@code @MessageExceptionHandler})이
 * {@link #buildErrorFrame}을 직접 호출해서 clientOutboundChannel로 보낸다.
 */
@Component
@RequiredArgsConstructor
public class ChatStompErrorHandler extends org.springframework.web.socket.messaging.StompSubProtocolErrorHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable exception) {
        return buildErrorFrame(exception, null);
    }

    /**
     * ERROR frame 메시지를 만든다. {@code sessionId}를 넘기면
     * {@code clientOutboundChannel}로 직접 보낼 때 어느 WebSocket 세션으로
     * 전달할지 알 수 있게 {@code simpSessionId} 헤더까지 채운다(handler 메서드
     * 예외 경로용). protocol 레벨 경로(null)는 Spring이 세션을 이미 알고 있어서
     * 필요 없다.
     */
    public Message<byte[]> buildErrorFrame(Throwable exception, String sessionId) {
        ErrorResponse error = errorResponse(exception);
        byte[] payload = json(error);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(error.code());
        accessor.setContentType(org.springframework.util.MimeTypeUtils.APPLICATION_JSON);
        accessor.setContentLength(payload.length);
        if (sessionId != null) {
            accessor.setSessionId(sessionId);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
    }

    private ErrorResponse errorResponse(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ApiException apiException) {
                return new ErrorResponse(apiException.getErrorCode().name(), apiException.getMessage());
            }
            if (current instanceof MethodArgumentNotValidException validationException) {
                String message = validationException.getBindingResult().getFieldErrors().stream()
                        .findFirst()
                        .map(error -> error.getDefaultMessage())
                        .orElse(ErrorCode.VALIDATION.getMessage());
                return ErrorResponse.validation(message);
            }
            if (current instanceof MessageConversionException || current instanceof JsonProcessingException) {
                return ErrorResponse.from(ErrorCode.VALIDATION);
            }
            current = current.getCause();
        }
        return ErrorResponse.from(ErrorCode.INTERNAL_ERROR);
    }

    private byte[] json(ErrorResponse error) {
        try {
            return objectMapper.writeValueAsBytes(error);
        } catch (JsonProcessingException exception) {
            return ("{\"code\":\"INTERNAL_ERROR\",\"message\":\""
                    + ErrorCode.INTERNAL_ERROR.getMessage()
                    + "\"}").getBytes(StandardCharsets.UTF_8);
        }
    }
}
