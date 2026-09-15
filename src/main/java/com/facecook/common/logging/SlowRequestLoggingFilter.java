package com.facecook.common.logging;

import com.facecook.common.session.SessionAuthenticationInterceptor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 응답시간이 임계값을 넘는 요청만 WARN으로 남긴다. 원인(GC 멈춤·커넥션 대기·
 * 느린 쿼리 등)을 가리지 않고 "어느 API가 얼마나 걸렸는지"부터 눈에 보이게
 * 하려는 목적이라, 필터 레벨(서블릿 처리 전체 시간)에서 잰다.
 */
@Slf4j
@Component
public class SlowRequestLoggingFilter extends OncePerRequestFilter {

    private final long thresholdMs;

    public SlowRequestLoggingFilter(
            @Value("${app.logging.slow-request.threshold-ms:500}") long thresholdMs
    ) {
        this.thresholdMs = thresholdMs;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            if (elapsedMs >= thresholdMs) {
                log.warn(
                        "SLOW {} {} - {} {}ms userId={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        elapsedMs,
                        SessionAuthenticationInterceptor.currentUserId(request)
                );
            }
        }
    }
}
