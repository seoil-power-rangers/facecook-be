package com.facecook.profile.dto;

import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /api/profile} 요청 본문. 바꿀 수 있는 건 학과·학년·자기소개·사진 네 개뿐이다
 * (닉네임·성별·나이 등은 가입 후 못 바꾼다).
 *
 * <p>필드가 null이면 "안 바꿈", 빈 문자열이면 "지움"이다({@code Profile#update}).
 * JSON에서 아예 빼면 null이 된다.</p>
 */
public record UpdateProfileRequest(
        @Size(max = 100, message = "학과는 100자를 넘을 수 없습니다.")
        String department,

        @Size(max = 20, message = "학년은 20자를 넘을 수 없습니다.")
        String grade,

        @Size(max = 500, message = "자기소개는 500자를 넘을 수 없습니다.")
        String bio,

        @Size(max = 500, message = "사진 URL은 500자를 넘을 수 없습니다.")
        String photo
) {
    /** 바꿀 항목이 하나라도 있는지. 없으면 {@code ProfileService#update}가 400으로 거절한다. */
    public boolean hasChanges() {
        return department != null || grade != null || bio != null || photo != null;
    }
}
