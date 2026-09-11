package com.facecook.cook.dto;

public record CookUsageResponse(
        long todayUsed,
        int dailyLimit,
        long totalUsed
) {
}
