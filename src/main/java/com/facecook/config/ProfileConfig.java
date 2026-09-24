package com.facecook.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * {@link ProfileActivityProperties}를 설정값 빈으로 등록한다. 등록만 하는
 * 설정 클래스라 본문이 비어 있다.
 */
@Configuration
@EnableConfigurationProperties(ProfileActivityProperties.class)
public class ProfileConfig {
}
