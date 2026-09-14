package com.facecook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.profile.activity")
public record ProfileActivityProperties(
        /** 부스 특성상 화장실 다녀오는 정도는 봐줘야 해서 5분보다 넉넉하게 잡는다. */
        @DefaultValue("15") long activeWindowMinutes
) {
}
