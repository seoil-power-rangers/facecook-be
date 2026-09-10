package com.facecook.common.session;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;

/**
 * 7일 유효 세션을 HMAC-SHA256으로 서명한 문자열 토큰으로 만든다.
 * 토큰엔 userId·만료시각만 담고, 역할·정지여부는 항상 DB에서 다시 조회한다
 * (계정 상태가 바뀌어도 즉시 반영되게 하기 위함 — 토큰 자체를 신뢰 소스로 두지 않음).
 */
@Component
@RequiredArgsConstructor
public class SessionTokenSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SessionProperties properties;
    private final Clock clock;

    public String issue(Long userId, long ttlSeconds) {
        long expiresAt = clock.instant().getEpochSecond() + ttlSeconds;
        String payload = userId + ":" + expiresAt;
        String encodedPayload = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = ENCODER.encodeToString(sign(encodedPayload));
        return encodedPayload + "." + signature;
    }

    public Optional<SessionToken> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        int separatorIndex = token.indexOf('.');
        if (separatorIndex < 0) {
            return Optional.empty();
        }

        String encodedPayload = token.substring(0, separatorIndex);
        String signature = token.substring(separatorIndex + 1);

        byte[] expectedSignature = sign(encodedPayload);
        byte[] actualSignature;
        try {
            actualSignature = DECODER.decode(signature);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            return Optional.empty();
        }

        return parsePayload(encodedPayload);
    }

    private Optional<SessionToken> parsePayload(String encodedPayload) {
        String payload;
        try {
            payload = new String(DECODER.decode(encodedPayload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        int separatorIndex = payload.indexOf(':');
        if (separatorIndex < 0) {
            return Optional.empty();
        }

        try {
            Long userId = Long.parseLong(payload.substring(0, separatorIndex));
            long expiresAt = Long.parseLong(payload.substring(separatorIndex + 1));
            SessionToken token = new SessionToken(userId, expiresAt);
            if (token.isExpired(clock.instant().getEpochSecond())) {
                return Optional.empty();
            }
            return Optional.of(token);
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private byte[] sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("세션 서명에 실패했습니다.", exception);
        }
    }
}
