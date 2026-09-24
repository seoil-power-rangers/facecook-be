package com.facecook.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해시 도구를 빈으로 등록한다. 가입({@code AuthService#verifyCodeAndCreateParticipant}),
 * 비밀번호 로그인({@code AuthService#login}), 슈퍼 계정 생성({@code SuperAccountBootstrap})이 쓴다.
 *
 * <p>BCrypt를 쓰는 이유: 같은 비밀번호도 매번 다른 해시가 나오고(무작위 salt), 일부러
 * 느리게 계산돼서 DB가 유출돼도 비밀번호를 대량으로 맞혀 보기 어렵다. 이 프로젝트는
 * Spring Security 전체가 아니라 이 해시 도구만 가져다 쓴다.</p>
 */
@Configuration
public class PasswordConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
