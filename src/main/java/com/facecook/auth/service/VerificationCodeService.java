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

/**
 * 이메일 인증코드(6자리)의 발급·확인·소비. Redis에만 저장하고 DB에는
 * 안 남는다 — {@link #CODE_TTL_SECONDS}가 지나면 Redis TTL로 자동 삭제.
 *
 * <p>Redis 키에는 이메일을 그대로 안 쓰고 SHA-256 해시로 바꿔서 쓴다
 * ({@link #hash}) — Redis 콘솔이나 로그를 통해 실제 이메일 주소가
 * 노출되지 않게 하기 위함. 코드 비교({@link #verify})도 일반
 * {@code equals} 대신 {@link MessageDigest#isEqual}로 상수 시간
 * 비교한다 — 응답 시간 차이로 코드를 한 자리씩 맞혀나가는 타이밍
 * 공격을 막는다.</p>
 */
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

    /**
     * email에 6자리 인증코드를 발급해서 메일로 보낸다(purpose별로 완전히
     * 독립된 코드 — 회원가입용과 로그인용이 동시에 유효할 수 있다).
     *
     * <p>전제조건: 같은 email·purpose 조합으로 {@link #RESEND_LIMIT_SECONDS}
     * (30초) 안에 재요청하지 않았어야 함.</p>
     *
     * <p>부작용: Redis에 코드를 {@link #CODE_TTL_SECONDS}(5분) TTL로
     * 저장하고 재발송 제한 키도 같이 세운다(둘 다 {@code setIfAbsent}로
     * 원자적 처리 — 동시 요청이 와도 둘 다 발급되는 일은 없다). 메일
     * 발송에 실패하면 방금 세운 두 Redis 키를 롤백(삭제)해서, "메일은
     * 안 갔는데 재발송 제한만 걸린" 상태가 남지 않게 한다.</p>
     *
     * <p>예외: {@code RESEND_TOO_SOON}.</p>
     *
     * @see #verify(String, String, VerificationPurpose)
     */
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

    /**
     * email·purpose에 발급된 코드와 입력값이 일치하는지 확인만 한다
     * (일치해도 코드를 소비하지 않음 — 소비는 {@link #consume}이 따로
     * 한다).
     *
     * <p>전제조건: {@link #issue}로 발급된 코드가 아직 만료 전이어야
     * 함.</p>
     *
     * <p>부작용: 없음.</p>
     *
     * <p>예외: {@code CODE_EXPIRED}(발급 이력 없음 또는 TTL 만료),
     * {@code CODE_INVALID}(코드 불일치).</p>
     *
     * @see #issue(String, VerificationPurpose)
     * @see #consume(String, VerificationPurpose)
     */
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

    /**
     * email·purpose에 발급된 코드를 지운다(재사용 방지 — {@link #verify}
     * 통과 후 실제 가입/로그인 처리까지 끝났을 때 호출자가 부른다).
     *
     * <p>전제조건: 없음(코드가 이미 없어도 에러 안 남 — Redis delete는
     * 키가 없어도 성공).</p>
     *
     * <p>부작용: Redis에서 코드 키만 지운다(재발송 제한 키는 안 건드림
     * — 그건 TTL로 자연 만료됨).</p>
     *
     * <p>예외 없음.</p>
     *
     * @see #verify(String, String, VerificationPurpose)
     */
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
