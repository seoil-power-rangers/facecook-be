package com.facecook.superaccount.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** {@link SuperAccountProperties}를 설정값 빈으로 등록한다. */
@Configuration
@EnableConfigurationProperties(SuperAccountProperties.class)
public class SuperAccountConfig {
}
