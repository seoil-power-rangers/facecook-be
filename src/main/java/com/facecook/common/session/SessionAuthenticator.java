package com.facecook.common.session;

import com.facecook.auth.entity.User;
import com.facecook.auth.entity.UserStatus;
import com.facecook.auth.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 세션 인증 정책(토큰 검증 → 사용자 조회 → 정지 여부 확인)을 한 곳에 둔다.
 *
 * <p>HTTP 요청({@link SessionAuthenticationInterceptor}), 채팅 WebSocket 핸드셰이크,
 * STOMP 프레임 재검증이 모두 이 순서를 따른다. 예전에는 세 곳이 같은 순서를 각자
 * 복사해 두어서, 정책을 바꿀 때 일부 진입점만 고쳐지는 구멍이 생길 수 있었다.</p>
 *
 * <p>실패를 어떻게 알리는지(HTTP는 쿠키 삭제와 예외, 핸드셰이크는 응답 상태, STOMP는
 * 오류 프레임)는 진입점마다 다르므로 이 클래스는 결과만 돌려주고 표현은 호출부에
 * 맡긴다.</p>
 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticator {

    private final SessionTokenSigner signer;
    private final UserRepository userRepository;

    /**
     * rawToken을 검증하고 계정 상태를 DB에서 다시 확인한다.
     *
     * <p>전제조건: 없음(rawToken이 null이어도 된다).</p>
     *
     * <p>부작용: 사용자 한 명을 조회한다. 역할·정지 여부를 토큰에 담지 않고 매번 DB를
     * 보는 이유는, 방금 정지된 계정(예: 신고 처리)의 이미 발급된 세션도 즉시 막기
     * 위해서다.</p>
     *
     * <p>예외 없음 — 실패는 {@link Result#outcome()}으로 돌려준다.</p>
     */
    public Result authenticate(String rawToken) {
        if (rawToken == null) {
            return Result.unauthorized();
        }
        Optional<SessionToken> sessionToken = signer.verify(rawToken);
        if (sessionToken.isEmpty()) {
            return Result.unauthorized();
        }
        Optional<User> user = userRepository.findById(sessionToken.get().userId());
        if (user.isEmpty()) {
            return Result.unauthorized();
        }
        if (user.get().getStatus() == UserStatus.SUSPENDED) {
            return new Result(Outcome.SUSPENDED, null);
        }
        return new Result(
                Outcome.AUTHENTICATED,
                new AuthenticatedUser(user.get().getId(), user.get().getEmail(), user.get().getRole())
        );
    }

    /** 요청에서 cookieName 쿠키의 값을 찾는다. HTTP 인터셉터와 WebSocket 핸드셰이크가 같이 쓴다. */
    public static Optional<String> readCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    public enum Outcome {
        AUTHENTICATED,
        /** 토큰 없음·서명 불일치·만료·사용자 없음. */
        UNAUTHORIZED,
        SUSPENDED
    }

    /** {@code user}는 {@link Outcome#AUTHENTICATED}일 때만 채워진다. */
    public record Result(Outcome outcome, AuthenticatedUser user) {
        static Result unauthorized() {
            return new Result(Outcome.UNAUTHORIZED, null);
        }
    }
}
