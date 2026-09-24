package com.facecook.admin.dto;

/**
 * 관리자 통계 응답. 모두 요청 시점의 개수다.
 *
 * <ul>
 * <li>{@code totalUsers}: {@code users} 전체 행 — 관리자·슈퍼 계정도 포함</li>
 * <li>{@code activeToday}: {@link com.facecook.admin.config.ActiveUserCriterion} 설정에 따라 다르게 센다</li>
 * <li>{@code totalCooks}: {@code cook} 전체 행(상태 무관), {@code totalMatches}: {@code match_info} 전체 행</li>
 * <li>{@code missionCleared}: 세 STEP을 모두 끝낸 매칭({@code current_step >= 4})</li>
 * <li>{@code pendingReports}: 처리 전 신고</li>
 * </ul>
 */
public record AdminStatsResponse(
        long totalUsers,
        long activeToday,
        long totalCooks,
        long totalMatches,
        long missionCleared,
        long pendingReports
) {
}
