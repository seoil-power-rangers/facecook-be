package com.facecook.chat.websocket;

import com.facecook.auth.entity.User;
import com.facecook.auth.repository.UserRepository;
import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.SessionCookieService;
import com.facecook.common.session.SessionToken;
import com.facecook.common.session.SessionTokenSigner;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatHandshakeInterceptorTest {

    private static final String COOKIE_NAME = "FACECOOK_SESSION";

    @Mock
    private SessionTokenSigner signer;

    @Mock
    private SessionCookieService cookieService;

    @Mock
    private UserRepository userRepository;

    private ChatHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ChatHandshakeInterceptor(signer, cookieService, userRepository);
        when(cookieService.cookieName()).thenReturn(COOKIE_NAME);
    }

    @Test
    void authenticatesSignedCookieAndStoresPrincipalSource() {
        User user = user(1L);
        when(signer.verify("signed-token")).thenReturn(Optional.of(new SessionToken(1L, 9999999999L)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Map<String, Object> attributes = new HashMap<>();
        Exchange exchange = exchange(new Cookie(COOKIE_NAME, "signed-token"));

        boolean accepted = interceptor.beforeHandshake(
                exchange.request(),
                exchange.response(),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(accepted).isTrue();
        assertThat(attributes.get(ChatSessionAttributes.SESSION_TOKEN)).isEqualTo("signed-token");
        assertThat(attributes.get(ChatSessionAttributes.AUTHENTICATED_USER))
                .isEqualTo(new AuthenticatedUser(1L, "chat@example.com", user.getRole()));
    }

    @Test
    void rejectsMissingCookieDuringHttpUpgrade() {
        Exchange exchange = exchange();

        boolean accepted = interceptor.beforeHandshake(
                exchange.request(),
                exchange.response(),
                mock(WebSocketHandler.class),
                new HashMap<>()
        );

        assertThat(accepted).isFalse();
        assertThat(exchange.servletResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void rejectsSuspendedUserDuringHttpUpgrade() {
        User user = user(1L);
        user.suspend();
        when(signer.verify("signed-token")).thenReturn(Optional.of(new SessionToken(1L, 9999999999L)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Exchange exchange = exchange(new Cookie(COOKIE_NAME, "signed-token"));

        boolean accepted = interceptor.beforeHandshake(
                exchange.request(),
                exchange.response(),
                mock(WebSocketHandler.class),
                new HashMap<>()
        );

        assertThat(accepted).isFalse();
        assertThat(exchange.servletResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    private Exchange exchange(Cookie... cookies) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setCookies(cookies);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        return new Exchange(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                servletResponse
        );
    }

    private User user(Long id) {
        User user = User.createParticipant("chat@example.com", LocalDateTime.of(2026, 9, 30, 12, 0));
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
            return user;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Exchange(
            ServletServerHttpRequest request,
            ServletServerHttpResponse response,
            MockHttpServletResponse servletResponse
    ) {
    }
}
