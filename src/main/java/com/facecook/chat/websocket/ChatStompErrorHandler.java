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

@Component
@RequiredArgsConstructor
public class ChatStompErrorHandler extends org.springframework.web.socket.messaging.StompSubProtocolErrorHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable exception) {
        ErrorResponse error = errorResponse(exception);
        byte[] payload = json(error);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(error.code());
        accessor.setContentType(org.springframework.util.MimeTypeUtils.APPLICATION_JSON);
        accessor.setContentLength(payload.length);
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
