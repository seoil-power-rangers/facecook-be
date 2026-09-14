package com.facecook.common.session;

import com.facecook.auth.entity.User;
import com.facecook.auth.repository.UserRepository;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionAuthenticationInterceptorTest {

    private static final String COOKIE_NAME = "FACECOOK_SESSION";
    private static final Long USER_ID = 7L;
    private static final String TOKEN = "valid-token";

    @Mock
    private SessionTokenSigner signer;

    @Mock
    private SessionCookieService cookieService;

    @Mock
    private UserRepository userRepository;

    private SessionAuthenticationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new SessionAuthenticationInterceptor(signer, cookieService, userRepository);
        when(cookieService.cookieName()).thenReturn(COOKIE_NAME);
    }

    @Test
    void setsAuthenticatedUserAttributeForValidSession() {
        givenValidSession(activeUser());

        MockHttpServletRequest request = requestWithCookie();
        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(result).isTrue();
        assertThat(request.getAttribute(SessionAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE))
                .isInstanceOfSatisfying(AuthenticatedUser.class, user -> assertThat(user.userId()).isEqualTo(USER_ID));
    }

    @Test
    void rejectsSuspendedUser() {
        givenValidSession(suspendedUser());

        assertThatThrownBy(() -> interceptor.preHandle(requestWithCookie(), new MockHttpServletResponse(), new Object()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SUSPENDED)
                );
    }

    private void givenValidSession(User user) {
        when(signer.verify(TOKEN)).thenReturn(Optional.of(new SessionToken(USER_ID, Long.MAX_VALUE)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private MockHttpServletRequest requestWithCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/profile");
        request.setCookies(new Cookie(COOKIE_NAME, TOKEN));
        return request;
    }

    private User activeUser() {
        User user = User.createParticipant("user@example.com", LocalDateTime.now());
        setId(user, USER_ID);
        return user;
    }

    private User suspendedUser() {
        User user = activeUser();
        user.suspend();
        return user;
    }

    private static void setId(User user, Long id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
