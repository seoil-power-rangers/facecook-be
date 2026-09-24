package com.facecook.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 프로필 사진 업로드용 S3 설정. 서버는 사진 파일을 직접 받지 않고, 브라우저가
 * S3에 바로 올릴 수 있는 서명된 URL만 만든다({@code ProfilePhotoUploadService}).
 */
@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    /**
     * 서명된 업로드 URL을 만드는 도구. 자격증명을 코드에 넣지 않았다 — AWS SDK
     * 기본 방식대로 EC2 인스턴스 역할(운영)이나 로컬 AWS 설정에서 찾는다.
     */
    @Bean
    public S3Presigner s3Presigner(S3Properties properties) {
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .build();
    }
}
