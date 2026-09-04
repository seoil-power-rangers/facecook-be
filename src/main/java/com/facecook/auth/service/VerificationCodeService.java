package com.facecook.auth.service;

import com.facecook.auth.dto.VerificationPurpose;
import com.facecook.auth.mail.AuthMailService;
import com.facecook.auth.support.EmailAddress;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    public static final int CODE_TTL_SECONDS = 5 * 60;
    public static final int RESEND_LIMIT_SECONDS = 30;

    private static final Duration CODE_TTL = Duration.ofSeconds(CODE_TTL_SECONDS);
    private static final Duration RESEND_TTL = Duration.ofSeconds(RESEND_LIMIT_SECONDS);
    private static final String KEY_PREFIX = "auth:verification:";

    private final StringRedisTemplate redisTemplate;
    private final AuthMailService mailService;
    private final SecureRandom secureRandom;

    public void issue(String email, VerificationPurpose purpose) {
        String codeKey = codeKey(email, purpose);
        String resendKey = resendKey(email, purpose);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(resendKey, "1", RESEND_TTL);

        if (!Boolean.TRUE.equals(acquired)) {
            throw new ApiException(ErrorCode.RESEND_TOO_SOON);
        }

        String code = "%06d".formatted(secureRandom.nextInt(1_000_000));
        try {
            redisTemplate.opsForValue().set(codeKey, code, CODE_TTL);
            mailService.sendVerificationCode(email, code, purpose);
        } catch (RuntimeException exception) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(resendKey);
            throw exception;
        }
    }

    public void verify(String email, String code, VerificationPurpose purpose) {
        String storedCode = redisTemplate.opsForValue().get(codeKey(email, purpose));
        if (storedCode == null) {
            throw new ApiException(ErrorCode.CODE_EXPIRED);
        }

        boolean matches = MessageDigest.isEqual(
                storedCode.getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8)
        );
        if (!matches) {
            throw new ApiException(ErrorCode.CODE_INVALID);
        }
    }

    public void consume(String email, VerificationPurpose purpose) {
        redisTemplate.delete(codeKey(email, purpose));
    }

    private String codeKey(String email, VerificationPurpose purpose) {
        return key(purpose, email, "code");
    }

    private String resendKey(String email, VerificationPurpose purpose) {
        return key(purpose, email, "resend");
    }

    private String key(VerificationPurpose purpose, String email, String suffix) {
        return KEY_PREFIX + purpose.value() + ":" + hash(EmailAddress.normalize(email)) + ":" + suffix;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
