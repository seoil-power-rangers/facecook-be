package com.facecook.mission.service;

/**
 * {@link MissionAssignmentWriter}가 배정할 미션 묶음이나 그 묶음의 특정 STEP 템플릿을
 * 찾지 못했을 때만 던진다. {@link IllegalStateException}을 상속해 두어, 이 예외를 모르는
 * 기존 호출자(잠금·트랜잭션 관련 코드 등)는 지금까지처럼 일반 {@code IllegalStateException}으로
 * 다뤄도 된다 — 다만 {@link MissionService}는 관리자 목록의 제외 사유를 분류할 때 이 예외
 * "타입"으로만 {@code NO_TEMPLATE}을 판단한다. 배정 과정에서 나는 다른 종류의
 * {@code IllegalStateException}(예: 락·트랜잭션 상태 오류)까지 템플릿 누락으로
 * 잘못 안내하지 않기 위해서다.
 */
public class MissionTemplateNotFoundException extends IllegalStateException {
    public MissionTemplateNotFoundException(String message) {
        super(message);
    }
}
