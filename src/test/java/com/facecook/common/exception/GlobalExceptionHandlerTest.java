package com.facecook.common.exception;

import com.facecook.auth.entity.UserRole;
import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.SessionAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsErrorCodeStatusAndBodyForAnonymousRequest() {
        HttpServletRequest request = new MockHttpServletRequest("POST", "/api/kok/1");

        ResponseEntity<ErrorResponse> response =
                handler.handleApiException(new ApiException(ErrorCode.DUPLICATE), request);

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.DUPLICATE.getStatus());
        assertThat(response.getBody().code()).isEqualTo("DUPLICATE");
    }

    @Test
    void returnsErrorCodeStatusAndBodyWhenRequesterIsAuthenticated() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/kok/1");
        request.setAttribute(
                SessionAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE,
                new AuthenticatedUser(42L, "user@example.com", UserRole.PARTICIPANT)
        );

        ResponseEntity<ErrorResponse> response =
                handler.handleApiException(new ApiException(ErrorCode.ALREADY_MATCHED), request);

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.ALREADY_MATCHED.getStatus());
        assertThat(response.getBody().code()).isEqualTo("ALREADY_MATCHED");
    }

    @Test
    void returnsNotFoundInsteadOf500ForMissingStaticResource() {
        ResponseEntity<Void> response = handler.handleNoResourceFound();

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
