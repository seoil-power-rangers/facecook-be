package com.facecook.cook.dto;

/**
 * 콕 사용량. {@code todayUsed}는 오늘(한국 날짜) 보낸 수 — 취소·거절된 콕도 센다.
 * {@code dailyLimit}는 {@code CookService.DAILY_LIMIT}, {@code totalUsed}는 지금까지 보낸 전체 수.
 */
public record CookUsageResponse(
        long todayUsed,
        int dailyLimit,
        long totalUsed
) {
}
