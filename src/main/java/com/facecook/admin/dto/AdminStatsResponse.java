package com.facecook.admin.dto;

public record AdminStatsResponse(
        long totalUsers,
        long activeToday,
        long totalCooks,
        long totalMatches,
        long missionCleared,
        long pendingReports
) {
}
