package com.facecook.profile.dto;

/** 홈 화면 "총 사용자"용. 참가자 300명 전체를 받아 .length로 세던 걸 대체한다. */
public record ProfileStatsResponse(long total, long activeNow) {
}
