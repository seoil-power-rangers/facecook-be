package com.facecook.profile.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.config.S3Properties;
import com.facecook.profile.dto.PhotoUploadUrlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfilePhotoUploadService {

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
    private final ProfilePhotoUrlPolicy photoUrlPolicy;

    public PhotoUploadUrlResponse issueUploadUrl(String contentType) {
        String extension = ALLOWED_CONTENT_TYPES.get(contentType);
        if (extension == null) {
            throw new ApiException(ErrorCode.VALIDATION, "지원하지 않는 이미지 형식입니다. (jpeg, png, webp만 가능)");
        }

        // 사용자ID·순번처럼 추측 가능한 값 대신 무작위 키를 써서, 다른
        // 사람의 사진 URL을 유추해 접근하지 못하게 한다.
        String key = "profile-photos/%s.%s".formatted(UUID.randomUUID(), extension);

        /*
         * storageClass는 일부러 지정하지 않는다 — S3는 지정 안 하면 이미
         * Standard가 기본값이라 결과는 같은데, PutObjectRequest에 넣는
         * 순간 presigned URL의 서명 대상 헤더(X-Amz-SignedHeaders)에
         * x-amz-storage-class가 끼어들어간다. 그러면 실제 PUT 요청도
         * 그 헤더를 토씨 하나 안 틀리고 같이 보내야 서명이 맞는데,
         * 업로드하는 쪽(프론트)은 그 헤더를 모르니 403(서명 불일치)이
         * 난다 — 실제로 겪은 장애다.
         *
         * IA(저빈도 접근)로 바꾸고 싶어질 수 있는데, 이 사진들은 행사
         * 종료(EVENT.purgeAt)와 함께 곧 지워지는 초단기 데이터라 IA의
         * 최소 30일 저장 요금을 다 못 채우고 삭제되고, 탐색 화면에서
         * 서로 자주 열어보는 데이터라 IA의 조회 요금·비싼 GET 단가까지
         * 겹쳐 오히려 Standard보다 비싸진다. 결론: 아무것도 지정하지
         * 않는 게 비용도 맞고 서명 문제도 없다.
         */
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(s3Properties.presignTtlSeconds()))
                .putObjectRequest(putObjectRequest)
                .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
        return new PhotoUploadUrlResponse(uploadUrl, photoUrlPolicy.urlFor(key));
    }
}
