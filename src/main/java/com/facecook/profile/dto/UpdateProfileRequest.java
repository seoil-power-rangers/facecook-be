package com.facecook.profile.dto;

import jakarta.validation.constraints.Size;

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
    public boolean hasChanges() {
        return department != null || grade != null || bio != null || photo != null;
    }
}
