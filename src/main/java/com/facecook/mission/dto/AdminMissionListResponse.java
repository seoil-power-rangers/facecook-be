package com.facecook.mission.dto;

import java.util.List;

/**
 * {@code ?includeExcluded=true}로 요청했을 때만 내려주는 관리자 미션 목록. 파라미터 없이
 * 요청하면(구 FE 호환) {@code items}만 배열 그대로 내려준다 — {@link
 * com.facecook.mission.controller.AdminMissionController} 참고.
 */
public record AdminMissionListResponse(
        List<AdminMissionProgressResponse> items,
        List<AdminMissionExclusionResponse> excluded
) {
}
