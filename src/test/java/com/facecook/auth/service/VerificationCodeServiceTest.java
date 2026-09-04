package com.facecook.auth.service;

import com.facecook.auth.dto.VerificationPurpose;
import com.facecook.auth.mail.AuthMailService;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.security.SecureRandom;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationCodeServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AuthMailService mailService;

    @Mock
    private SecureRandom secureRandom;

    private VerificationCodeService verificationCodeService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        verificationCodeService = new VerificationCodeService(redisTemplate, mailService, secureRandom);
    }

    @Test
    void issuesSixDigitCodeWithTtlAndResendLimit() {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(30))))
                .thenReturn(true);
        when(secureRandom.nextInt(1_000_000)).thenReturn(42);

        verificationCodeService.issue("user@example.com", VerificationPurpose.SIGNUP);

        ArgumentCaptor<String> codeKey = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(codeKey.capture(), eq("000042"), eq(Duration.ofMinutes(5)));
        verify(mailService).sendVerificationCode(
                "user@example.com",
                "000042",
                VerificationPurpose.SIGNUP
        );
        assertThat(codeKey.getValue()).doesNotContain("user@example.com");
    }

    @Test
    void blocksRequestDuringResendLimit() {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(30))))
                .thenReturn(false);

        assertErrorCode(
                () -> verificationCodeService.issue("user@example.com", VerificationPurpose.LOGIN),
                ErrorCode.RESEND_TOO_SOON
        );
        verify(mailService, never()).sendVerificationCode(anyString(), anyString(), eq(VerificationPurpose.LOGIN));
    }

    @Test
    void removesRedisValuesWhenMailSendingFails() {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(30))))
                .thenReturn(true);
        when(secureRandom.nextInt(1_000_000)).thenReturn(123456);
        doThrow(new ApiException(ErrorCode.EMAIL_SEND_FAILED))
                .when(mailService)
                .sendVerificationCode("user@example.com", "123456", VerificationPurpose.SIGNUP);

        assertErrorCode(
                () -> verificationCodeService.issue("user@example.com", VerificationPurpose.SIGNUP),
                ErrorCode.EMAIL_SEND_FAILED
        );
        verify(redisTemplate, times(2)).delete(anyString());
    }

    @Test
    void returnsExpiredWhenStoredCodeDoesNotExist() {
        when(valueOperations.get(anyString())).thenReturn(null);

        assertErrorCode(
                () -> verificationCodeService.verify(
                        "user@example.com",
                        "123456",
                        VerificationPurpose.LOGIN
                ),
                ErrorCode.CODE_EXPIRED
        );
    }

    @Test
    void returnsInvalidWhenCodeDoesNotMatch() {
        when(valueOperations.get(anyString())).thenReturn("123456");

        assertErrorCode(
                () -> verificationCodeService.verify(
                        "user@example.com",
                        "654321",
                        VerificationPurpose.LOGIN
                ),
                ErrorCode.CODE_INVALID
        );
    }

    @Test
    void acceptsMatchingCode() {
        when(valueOperations.get(anyString())).thenReturn("123456");

        verificationCodeService.verify("user@example.com", "123456", VerificationPurpose.LOGIN);
    }

    private void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(expected)
                );
    }
}
