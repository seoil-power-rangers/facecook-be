package com.facecook.common.session;

import com.facecook.auth.entity.UserRole;
import com.facecook.auth.service.UserActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ActivityTrackingInterceptorTest {

    private static final Long USER_ID = 7L;

    @Mock
    private UserActivityService userActivityService;

    private ActivityTrackingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ActivityTrackingInterceptor(userActivityService);
    }

    @Test
    void touchesTheAuthenticatedUserFromTheRequestAttribute() {
        MockHttpServletRequest request = requestWithAuthenticatedUser();

        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(result).isTrue();
        verify(userActivityService).touch(USER_ID);
    }

    @Test
    void doesNothingWhenNoAuthenticatedUserAttributeIsPresent() {
        // SessionAuthenticationInterceptor가 먼저 안 걸렸으면(예: 공개 경로)
        // attribute가 없다 — 이 경우 활동기록을 시도하면 안 된다.
        MockHttpServletRequest request = new MockHttpServletRequest();

        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(result).isTrue();
        verify(userActivityService, never()).touch(any());
    }

    @Test
    void proceedsEvenWhenActivityTrackingFails() {
        MockHttpServletRequest request = requestWithAuthenticatedUser();
        doThrow(new RuntimeException("db hiccup")).when(userActivityService).touch(USER_ID);

        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(result).isTrue();
    }

    private MockHttpServletRequest requestWithAuthenticatedUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                SessionAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE,
                new AuthenticatedUser(USER_ID, "user@example.com", UserRole.PARTICIPANT)
        );
        return request;
    }
}
