package com.facecook.config;

import com.facecook.common.session.ActivityTrackingInterceptor;
import com.facecook.common.session.CurrentUserArgumentResolver;
import com.facecook.common.session.SessionAuthenticationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private static final String[] PUBLIC_AUTH_PATHS = {
            "/api/auth/request-code",
            "/api/auth/verify-signup",
            "/api/auth/verify-login",
            "/api/auth/login",
            "/api/auth/logout"
    };

    private final CorsProperties corsProperties;
    private final SessionAuthenticationInterceptor sessionAuthenticationInterceptor;
    private final ActivityTrackingInterceptor activityTrackingInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sessionAuthenticationInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(PUBLIC_AUTH_PATHS);
        // sessionAuthenticationInterceptor 다음 순서로 등록해야 한다 — 이
        // 인터셉터가 CURRENT_USER_ATTRIBUTE를 먼저 채워둬야 활동기록이 누구
        // 것인지 알 수 있다.
        registry.addInterceptor(activityTrackingInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(PUBLIC_AUTH_PATHS);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
