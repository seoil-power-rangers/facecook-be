package com.facecook.profile.dto;

/**
 * 사진 업로드 안내. FE는 {@code uploadUrl}로 S3에 파일을 직접 PUT하고, 성공하면
 * {@code photoUrl}을 프로필 작성·수정 요청의 {@code photo}에 넣는다.
 */
public record PhotoUploadUrlResponse(String uploadUrl, String photoUrl) {
}
