package com.facecook.common.session;

import com.facecook.auth.entity.UserRole;

/**
 * 이번 요청을 보낸 로그인 사용자. {@link SessionAuthenticator}가 세션 토큰을
 * 검증하고 DB에서 계정을 다시 읽은 뒤 만든다(이 클래스를 만드는 곳은 그 한 곳뿐).
 *
 * <p>흐름: {@code SessionAuthenticationInterceptor}가 요청 속성
 * {@code currentUser}에 넣음 → 컨트롤러 파라미터에 {@code @CurrentUser}를 붙이면
 * {@link CurrentUserArgumentResolver}가 꺼내 준다. WebSocket에서는
 * {@code ChatPrincipal}이 이 값을 감싼다.</p>
 *
 * <p>{@code role}은 토큰이 아니라 DB에서 읽은 값이라, 권한이 바뀌면 다음
 * 요청부터 바로 반영된다.</p>
 */
public record AuthenticatedUser(Long userId, String email, UserRole role) {
}
