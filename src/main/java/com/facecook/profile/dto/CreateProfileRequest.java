package com.facecook.profile.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/profile} 요청 본문(온보딩에서 입력한 값).
 *
 * <p>검증은 두 단계다. 형식(빈 값·길이·19세 이상)은 여기 어노테이션이 컨트롤러 진입 전에 보고,
 * 값의 의미(학과가 정해진 30개 중 하나인지, 사진이 우리 버킷 주소인지)는
 * {@code ProfileService}가 본다. {@code @Min(19)}는 행사 참가 조건이다(FE도 막지만
 * API를 직접 부르는 경우를 위해 서버에서도 막는다).</p>
 *
 * <p>{@code age}를 {@code int}가 아니라 {@code Integer}로 받는 이유: JSON에 값이 없으면
 * {@code int}는 조용히 0이 되지만 {@code Integer}는 null이 되어 {@code @NotNull}이 잡는다.</p>
 */
public record CreateProfileRequest(
        @NotBlank(message = "닉네임을 입력해주세요.")
        @Size(max = 50, message = "닉네임은 50자를 넘을 수 없습니다.")
        String nickname,

        @NotBlank(message = "성별을 입력해주세요.")
        @Size(max = 10, message = "성별은 10자를 넘을 수 없습니다.")
        String gender,

        @NotNull(message = "나이를 입력해주세요.")
        @Min(value = 19, message = "나이는 19세 이상이어야 합니다.")
        Integer age,

        @NotBlank(message = "MBTI를 입력해주세요.")
        @Size(max = 4, message = "MBTI는 4자를 넘을 수 없습니다.")
        String mbti,

        @NotBlank(message = "취미를 입력해주세요.")
        @Size(max = 255, message = "취미는 255자를 넘을 수 없습니다.")
        String hobby,

        @NotBlank(message = "혈액형을 입력해주세요.")
        @Size(max = 5, message = "혈액형은 5자를 넘을 수 없습니다.")
        String bloodType,

        @Size(max = 100, message = "학과는 100자를 넘을 수 없습니다.")
        String department,

        @Size(max = 20, message = "학년은 20자를 넘을 수 없습니다.")
        String grade,

        @Size(max = 500, message = "자기소개는 500자를 넘을 수 없습니다.")
        String bio,

        @Size(max = 255, message = "이상형은 255자를 넘을 수 없습니다.")
        String idealType,

        @Size(max = 500, message = "사진 URL은 500자를 넘을 수 없습니다.")
        String photo
) {
}
