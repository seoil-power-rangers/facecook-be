package com.facecook.superaccount.dto;

import com.facecook.auth.entity.User;
import com.facecook.profile.entity.Profile;

public record SuperChatMemberResponse(
        Long userId,
        String email,
        String nickname,
        String gender,
        String photo
) {
    public static SuperChatMemberResponse from(User user, Profile profile) {
        return new SuperChatMemberResponse(
                user.getId(),
                user.getEmail(),
                profile == null ? null : profile.getNickname(),
                profile == null ? null : profile.getGender(),
                profile == null ? null : profile.getPhoto()
        );
    }
}
