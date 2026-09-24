package com.facecook.auth.support;

import java.util.Locale;

/**
 * 이메일 비교 기준을 한 곳에 둔다: 앞뒤 공백 제거 + 소문자. 가입·로그인·인증코드 키·
 * 슈퍼 계정 모두 이걸 거쳐서 {@code "A@x.com "}과 {@code "a@x.com"}이 같은 계정으로 취급된다.
 */
public final class EmailAddress {

    private EmailAddress() {
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
