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
