package com.facecook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * "지금 활동 중" 판정 기준(마지막 활동 후 몇 분까지). {@code ProfileActivityLookup}이
 * 쓴다. {@code @DefaultValue}는 설정 파일에 값이 없을 때 쓸 기본값이다.
 */
@ConfigurationProperties(prefix = "app.profile.activity")
public record ProfileActivityProperties(
        /** 부스 특성상 화장실 다녀오는 정도는 봐줘야 해서 5분보다 넉넉하게 잡는다. */
        @DefaultValue("15") long activeWindowMinutes
) {
}
