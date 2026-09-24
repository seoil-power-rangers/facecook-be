package com.facecook.common.session;

/**
 * 세션 쿠키 안에 든 정보: 누구({@code userId})의 세션이고 언제 만료되는지.
 * {@link SessionTokenSigner#verify}가 서명을 확인한 뒤 만든다. 역할·정지
 * 여부는 일부러 담지 않는다({@link SessionAuthenticator}가 DB에서 매번 확인).
 */
public record SessionToken(Long userId, long expiresAtEpochSeconds) {

    public boolean isExpired(long nowEpochSeconds) {
        return expiresAtEpochSeconds <= nowEpochSeconds;
    }
}
