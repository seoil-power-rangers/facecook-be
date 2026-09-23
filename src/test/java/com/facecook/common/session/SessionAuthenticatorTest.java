package com.facecook.common.session;

import com.facecook.auth.entity.User;
import com.facecook.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.Cookie;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionAuthenticatorTest {

    private SessionTokenSigner signer;
    private UserRepository userRepository;
    private SessionAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        signer = mock(SessionTokenSigner.class);
        userRepository = mock(UserRepository.class);
        authenticator = new SessionAuthenticator(signer, userRepository);
    }

    @Test
    void authenticatesActiveUserWithValidToken() {
        when(signer.verify("token")).thenReturn(Optional.of(new SessionToken(1L, 9999999999L)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));

        SessionAuthenticator.Result result = authenticator.authenticate("token");

        assertThat(result.outcome()).isEqualTo(SessionAuthenticator.Outcome.AUTHENTICATED);
        assertThat(result.user().userId()).isEqualTo(1L);
        assertThat(result.user().email()).isEqualTo("user@example.com");
    }

    @Test
    void missingTokenIsUnauthorizedWithoutTouchingSignerOrDatabase() {
        SessionAuthenticator.Result result = authenticator.authenticate(null);

        assertThat(result.outcome()).isEqualTo(SessionAuthenticator.Outcome.UNAUTHORIZED);
        assertThat(result.user()).isNull();
        verify(signer, never()).verify(any());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void invalidTokenIsUnauthorizedWithoutTouchingDatabase() {
        when(signer.verify("forged")).thenReturn(Optional.empty());

        SessionAuthenticator.Result result = authenticator.authenticate("forged");

        assertThat(result.outcome()).isEqualTo(SessionAuthenticator.Outcome.UNAUTHORIZED);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void validTokenForDeletedUserIsUnauthorized() {
        when(signer.verify("token")).thenReturn(Optional.of(new SessionToken(1L, 9999999999L)));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(authenticator.authenticate("token").outcome())
                .isEqualTo(SessionAuthenticator.Outcome.UNAUTHORIZED);
    }

    @Test
    void suspendedUserIsRejectedEvenWithValidToken() {
        User suspended = user(1L);
        suspended.suspend();
        when(signer.verify("token")).thenReturn(Optional.of(new SessionToken(1L, 9999999999L)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(suspended));

        SessionAuthenticator.Result result = authenticator.authenticate("token");

        assertThat(result.outcome()).isEqualTo(SessionAuthenticator.Outcome.SUSPENDED);
        assertThat(result.user()).isNull();
    }

    @Test
    void readsNamedCookieAndIgnoresOthers() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "x"), new Cookie("FACECOOK_SESSION", "token"));

        assertThat(SessionAuthenticator.readCookie(request, "FACECOOK_SESSION")).contains("token");
    }

    @Test
    void readCookieIsEmptyWhenRequestHasNoCookies() {
        assertThat(SessionAuthenticator.readCookie(new MockHttpServletRequest(), "FACECOOK_SESSION")).isEmpty();
    }

    private static User user(Long id) {
        User user = User.createParticipant("user@example.com", LocalDateTime.of(2026, 9, 30, 12, 0));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
