package com.facecook.mission.dto;

/**
 * 미션 배정 실패 등으로 관리자 목록에서 빠진 매칭. {@code reason}은 {@code NO_TEMPLATE}
 * (배정할 미션 묶음·템플릿을 찾지 못함) 또는 {@code UNKNOWN}(그 밖의 예상 외 오류) 중 하나다.
 */
public record AdminMissionExclusionResponse(Long matchId, String reason) {
}
