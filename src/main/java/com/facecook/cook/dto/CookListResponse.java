package com.facecook.cook.dto;

import java.util.List;

public record CookListResponse(
        List<CookItemResponse> sent,
        List<CookItemResponse> received,
        CookUsageResponse usage
) {
}
