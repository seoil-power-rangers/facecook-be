package com.facecook.superaccount.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SuperAccountProperties.class)
public class SuperAccountConfig {
}
