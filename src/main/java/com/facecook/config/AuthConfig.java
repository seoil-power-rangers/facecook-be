package com.facecook.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

/**
 * 인증(메일 인증코드) 관련 빈 설정. 메일 설정값({@link AuthMailProperties})을
 * 등록하고, 인증코드를 만들 난수 생성기를 빈으로 둔다.
 */
@Configuration
@EnableConfigurationProperties(AuthMailProperties.class)
public class AuthConfig {

    /**
     * 인증코드 6자리를 뽑는 난수 생성기({@code VerificationCodeService}가 주입받음).
     * {@code java.util.Random}이 아니라 {@code SecureRandom}을 쓰는 이유: 일반 Random은
     * 앞의 값들로 다음 값을 예측할 수 있어 인증코드에 쓰면 안 된다. 빈으로 두면
     * 테스트에서 고정된 값을 내는 가짜로 바꿔 끼울 수 있다.
     */
    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
