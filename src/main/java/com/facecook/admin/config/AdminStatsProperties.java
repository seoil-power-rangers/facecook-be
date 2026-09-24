package com.facecook.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.admin.stats.*} 설정. 환경변수 {@code ADMIN_STATS_ACTIVE_USER_CRITERION}으로 바꾸고,
 * 없으면 {@code STATUS}다(application.yml과 {@code @DefaultValue} 모두 같은 값). 문자열은 enum 이름으로 바뀐다.
 */
@ConfigurationProperties(prefix = "app.admin.stats")
public record AdminStatsProperties(
        @DefaultValue("STATUS") ActiveUserCriterion activeUserCriterion
) {
}
