package com.facecook.auth.entity;

/**
 * 계정 역할. 참가자(일반 사용자), 관리자(부스 운영진 — 미션 확인·신고 처리·통계),
 * 슈퍼(전체 대화 열람 등 최고 권한). DB에는 {@link UserRoleConverter}가 소문자로 저장한다.
 */
public enum UserRole {
    PARTICIPANT,
    ADMIN,
    SUPER
}
