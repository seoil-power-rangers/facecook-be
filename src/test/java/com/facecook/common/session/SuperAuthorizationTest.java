package com.facecook.common.session;

import com.facecook.auth.entity.UserRole;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuperAuthorizationTest {

    @Test
    void allowsSuper() {
        var superUser = new AuthenticatedUser(1L, "rhgustjrwkwlxjf", UserRole.SUPER);

        assertThatCode(() -> SuperAuthorization.requireSuper(superUser)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAdmin() {
        var admin = new AuthenticatedUser(2L, "admin@example.com", UserRole.ADMIN);

        assertThatThrownBy(() -> SuperAuthorization.requireSuper(admin))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void rejectsParticipant() {
        var participant = new AuthenticatedUser(3L, "user@example.com", UserRole.PARTICIPANT);

        assertThatThrownBy(() -> SuperAuthorization.requireSuper(participant))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
