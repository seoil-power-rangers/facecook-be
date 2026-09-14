package com.facecook.profile.dto;

import com.facecook.profile.entity.Profile;

import java.time.LocalDateTime;

public record ProfileResponse(
        Long userId,
        String nickname,
        String gender,
        Integer age,
        String mbti,
        String hobby,
        String bloodType,
        String department,
        String grade,
        String bio,
        String idealType,
        String photo,
        /** 기능명세 2절 "현재 활동 중 표시"의 근거 시각. 활동 기록이 없으면 null. */
        LocalDateTime lastActiveAt,
        /** 활성 기준(app.profile.activity.active-window-minutes)은 서버만 안다 — 클라이언트가 각자 계산하면 기준이 어긋난다. */
        boolean isActive
) {
    /** lastActiveAt·isActive는 별도 테이블(users)에서 오므로 ProfileActivityLookup을 거쳐 채운다. */
    public static ProfileResponse from(Profile profile, LocalDateTime lastActiveAt, boolean isActive) {
        return new ProfileResponse(
                profile.getUserId(),
                profile.getNickname(),
                profile.getGender(),
                profile.getAge(),
                profile.getMbti(),
                profile.getHobby(),
                profile.getBloodType(),
                profile.getDepartment(),
                profile.getGrade(),
                profile.getBio(),
                profile.getIdealType(),
                profile.getPhoto(),
                lastActiveAt,
                isActive
        );
    }
}
