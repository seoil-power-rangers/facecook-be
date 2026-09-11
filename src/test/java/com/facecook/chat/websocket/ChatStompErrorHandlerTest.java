package com.facecook.chat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ChatStompErrorHandlerTest {

    private final ChatStompErrorHandler errorHandler = new ChatStompErrorHandler(new ObjectMapper());

    @Test
    void serializesApiExceptionAsStompErrorFrame() {
        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(
                MessageBuilder.withPayload(new byte[0]).build(),
                new IllegalStateException(new ApiException(ErrorCode.CLOSED))
        );

        assertThat(new String(result.getPayload(), StandardCharsets.UTF_8))
                .isEqualTo("{\"code\":\"CLOSED\",\"message\":\"채팅 운영시간이 아닙니다.\"}");
    }
}
