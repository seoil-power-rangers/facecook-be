package com.facecook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 프로필 사진 버킷 설정({@code app.s3.*}). {@code publicBaseUrl}이 비어 있으면
 * {@code ProfilePhotoUrlPolicy}가 버킷·리전으로 기본 S3 주소를 만든다.
 */
@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(String bucket, String region, String publicBaseUrl, long presignTtlSeconds) {

    public S3Properties {
        publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl;
    }
}
