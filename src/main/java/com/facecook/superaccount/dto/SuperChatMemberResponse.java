package com.facecook.superaccount.dto;

import com.facecook.auth.entity.User;
import com.facecook.profile.entity.Profile;

/**
 * 슈퍼 채팅방 목록의 참가자 한 명. 프로필이 없으면 닉네임·성별·사진이 null이다.
 * 계정 자체를 못 찾으면 {@code SuperAccountService}가 {@code userId}만 채워서 만든다.
 */
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
