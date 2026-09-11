package com.facecook.common.session;

import com.facecook.auth.entity.UserRole;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAuthorizationTest {

    @Test
    void allowsAdmin() {
        var admin = new AuthenticatedUser(1L, "admin@example.com", UserRole.ADMIN);

        assertThatCode(() -> AdminAuthorization.requireAdmin(admin)).doesNotThrowAnyException();
    }

    @Test
    void rejectsParticipant() {
        var participant = new AuthenticatedUser(2L, "user@example.com", UserRole.PARTICIPANT);

        assertThatThrownBy(() -> AdminAuthorization.requireAdmin(participant))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
