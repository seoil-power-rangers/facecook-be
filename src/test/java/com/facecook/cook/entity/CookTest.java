package com.facecook.cook.entity;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CookTest {

    private static final long SENDER = 1L;
    private static final long RECEIVER = 2L;

    @Test
    void senderCancelsPendingCook() {
        Cook cook = pendingCook();

        cook.cancel(SENDER);

        assertThat(cook.getStatus()).isEqualTo(CookStatus.CANCELLED);
    }

    @Test
    void cancelingAlreadyCancelledCookKeepsStatusAndSucceeds() {
        Cook cook = pendingCook();
        cook.cancel(SENDER);

        cook.cancel(SENDER);

        assertThat(cook.getStatus()).isEqualTo(CookStatus.CANCELLED);
    }

    @Test
    void cancelRejectsNonSenderBeforeAnyStatusCheck() {
        Cook matched = pendingCook();
        matched.match(20L);

        assertThatThrownBy(() -> matched.cancel(RECEIVER)).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void cancelRejectsMatchedCookAndKeepsStatus() {
        Cook cook = pendingCook();
        cook.match(20L);

        assertThatThrownBy(() -> cook.cancel(SENDER)).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_MATCHED));
        assertThat(cook.getStatus()).isEqualTo(CookStatus.MATCHED);
    }

    @Test
    void cancelRejectsLegacyExpiredCookAndKeepsStatus() {
        Cook cook = pendingCook();
        ReflectionTestUtils.setField(cook, "status", CookStatus.EXPIRED);

        assertThatThrownBy(() -> cook.cancel(SENDER)).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_EXPIRED));
        assertThat(cook.getStatus()).isEqualTo(CookStatus.EXPIRED);
    }

    private static Cook pendingCook() {
        return Cook.pending(SENDER, RECEIVER, LocalDateTime.of(2026, 9, 21, 12, 0));
    }
}
