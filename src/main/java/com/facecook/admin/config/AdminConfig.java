package com.facecook.admin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** {@link AdminStatsProperties}를 설정값 빈으로 등록한다. */
@Configuration
@EnableConfigurationProperties(AdminStatsProperties.class)
public class AdminConfig {
}
