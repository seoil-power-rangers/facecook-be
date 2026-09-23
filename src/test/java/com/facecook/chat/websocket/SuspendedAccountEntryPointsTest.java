package com.facecook.chat.websocket;

import com.facecook.auth.entity.User;
import com.facecook.auth.repository.UserRepository;
import com.facecook.chat.service.ChatAuthorizationService;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.common.session.SessionAuthenticationInterceptor;
import com.facecook.common.session.SessionAuthenticator;
import com.facecook.common.session.SessionCookieService;
import com.facecook.common.session.SessionToken;
import com.facecook.common.session.SessionTokenSigner;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * 세 진입점(HTTP 요청, 채팅 WebSocket 핸드셰이크, STOMP 프레임)이 같은 정지 계정을
 * 똑같이 거절하는지 한 번에 확인한다. 세 곳이 같은 {@link SessionAuthenticator}를 쓰는지가
 * 이 테스트의 요점이다 — 정책을 한 곳에 모은 목적이 "일부 진입점만 고쳐지는 구멍"을
 * 없애는 것이기 때문이다. 거절을 알리는 방식은 진입점마다 다르게 유지한다.
 */
class SuspendedAccountEntryPointsTest {

    private static final String COOKIE_NAME = "FACECOOK_SESSION";
    private static final String TOKEN = "signed-token";

    private SessionCookieService cookieService;
    private ChatAuthorizationService chatAuthorizationService;
    private SessionAuthenticationInterceptor httpEntry;
    private ChatHandshakeInterceptor handshakeEntry;
    private ChatInboundChannelInterceptor stompEntry;

    @BeforeEach
    void setUp() {
        SessionTokenSigner signer = mock(SessionTokenSigner.class);
        UserRepository userRepository = mock(UserRepository.class);
        cookieService = mock(SessionCookieService.class);
        chatAuthorizationService = mock(ChatAuthorizationService.class);

        User suspended = User.createParticipant("suspended@example.com", LocalDateTime.of(2026, 9, 30, 12, 0));
        ReflectionTestUtils.setField(suspended, "id", 1L);
        suspended.suspend();
        when(signer.verify(TOKEN)).thenReturn(Optional.of(new SessionToken(1L, 9999999999L)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(suspended));
        when(cookieService.cookieName()).thenReturn(COOKIE_NAME);
        doAnswer(invocation -> {
            MockHttpServletResponse response = invocation.getArgument(0);
            response.addHeader(HttpHeaders.SET_COOKIE, COOKIE_NAME + "=; Max-Age=0");
            return null;
        }).when(cookieService).clear(any());

        SessionAuthenticator sharedPolicy = new SessionAuthenticator(signer, userRepository);
        httpEntry = new SessionAuthenticationInterceptor(sharedPolicy, cookieService);
        handshakeEntry = new ChatHandshakeInterceptor(sharedPolicy, cookieService);
        stompEntry = new ChatInboundChannelInterceptor(sharedPolicy, chatAuthorizationService);
    }

    @Test
    void httpRequestIsRejectedAsSuspendedAndSessionCookieIsCleared() {
        MockHttpServletRequest request = requestWithSessionCookie();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> httpEntry.preHandle(request, response, new Object()))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SUSPENDED));
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).startsWith(COOKIE_NAME + "=;");
        assertThat(request.getAttribute(SessionAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE)).isNull();
    }

    @Test
    void websocketHandshakeIsRefusedWithSuspendedStatus() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = handshakeEntry.beforeHandshake(
                new ServletServerHttpRequest(requestWithSessionCookie()),
                new ServletServerHttpResponse(servletResponse),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(accepted).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(ErrorCode.SUSPENDED.getStatus().value());
        assertThat(attributes).isEmpty();
    }

    @Test
    void stompFrameIsRejectedAsSuspendedBeforeAnyChatAuthorization() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/chat/20/send");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ChatSessionAttributes.SESSION_TOKEN, TOKEN);
        accessor.setSessionAttributes(attributes);
        accessor.setLeaveMutable(true);

        assertThatThrownBy(() -> stompEntry.preSend(
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()),
                mock(MessageChannel.class)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SUSPENDED));
        verifyNoInteractions(chatAuthorizationService);
    }

    private MockHttpServletRequest requestWithSessionCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/profile");
        request.setCookies(new Cookie(COOKIE_NAME, TOKEN));
        return request;
    }
}
