package com.facecook.profile.service;

import com.facecook.config.S3Properties;
import org.springframework.stereotype.Component;

/**
 * 우리 S3 버킷이 내주는 사진 URL 형식을 한 곳에서 정한다. 발급(업로드 URL
 * API)과 검증(프로필 저장 시 photo 필드)이 서로 다른 규칙을 쓰면 조용히
 * 어긋날 수 있어서, 두 서비스가 이 클래스 하나를 같이 쓴다.
 */
@Component
public class ProfilePhotoUrlPolicy {

    private final String baseUrl;

    public ProfilePhotoUrlPolicy(S3Properties properties) {
        this.baseUrl = properties.publicBaseUrl().isBlank()
                ? "https://%s.s3.%s.amazonaws.com".formatted(properties.bucket(), properties.region())
                : stripTrailingSlash(properties.publicBaseUrl());
    }

    public String urlFor(String objectKey) {
        return baseUrl + "/" + objectKey;
    }

    /** 프로필 photo 필드에 우리 버킷이 아닌 임의의 URL이 그대로 저장되는 것을 막는다. */
    public boolean isOwnPhotoUrl(String url) {
        return url.startsWith(baseUrl + "/");
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
