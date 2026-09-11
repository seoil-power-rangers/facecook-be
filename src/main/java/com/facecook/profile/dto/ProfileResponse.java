package com.facecook.profile.dto;

import com.facecook.profile.entity.Profile;

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
        String photo
) {
    public static ProfileResponse from(Profile profile) {
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
                profile.getPhoto()
        );
    }
}
