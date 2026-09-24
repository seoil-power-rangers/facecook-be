package com.facecook.push.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 웹 푸시 설정값({@link VapidProperties})을 빈으로 등록한다. */
@Configuration
@EnableConfigurationProperties(VapidProperties.class)
public class PushConfig {
}
