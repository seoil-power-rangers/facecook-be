package com.facecook.superaccount.dto;

import com.facecook.auth.entity.User;
import com.facecook.profile.entity.Profile;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 슈퍼 계정의 전체 사용자 목록 한 줄: 계정 정보 + 프로필 전체. 프로필이 없는 계정(관리자·슈퍼, 프로필 작성 전
 * 참가자)은 프로필 필드가 모두 null이다. 역할·상태는 소문자 문자열이다.
 */
public record SuperUserResponse(
        Long userId,
        String email,
        String role,
        String status,
        LocalDateTime createdAt,
        LocalDateTime lastActiveAt,
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
        String photo
) {
    public static SuperUserResponse from(User user, Profile profile) {
        return new SuperUserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name().toLowerCase(Locale.ROOT),
                user.getStatus().name().toLowerCase(Locale.ROOT),
                user.getCreatedAt(),
                user.getLastActiveAt(),
                profile == null ? null : profile.getNickname(),
                profile == null ? null : profile.getGender(),
                profile == null ? null : profile.getAge(),
                profile == null ? null : profile.getMbti(),
                profile == null ? null : profile.getHobby(),
                profile == null ? null : profile.getBloodType(),
                profile == null ? null : profile.getDepartment(),
                profile == null ? null : profile.getGrade(),
                profile == null ? null : profile.getBio(),
                profile == null ? null : profile.getIdealType(),
                profile == null ? null : profile.getPhoto()
        );
    }
}
