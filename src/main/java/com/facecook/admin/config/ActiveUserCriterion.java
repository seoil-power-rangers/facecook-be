package com.facecook.admin.config;

/**
 * 관리자 통계의 {@code activeToday}를 세는 기준({@link AdminStatsProperties}).
 * {@code STATUS}: 정지되지 않은 계정 전부. {@code LAST_ACTIVE_TODAY}: 오늘(행사 시간대 자정부터) 활동 기록이 있는 계정.
 */
public enum ActiveUserCriterion {
    STATUS,
    LAST_ACTIVE_TODAY
}
