package com.facecook.profile.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.config.S3Properties;
import com.facecook.profile.dto.PhotoUploadUrlResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfilePhotoUploadServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedPutObjectRequest presignedRequest;

    private ProfilePhotoUploadService service;

    @BeforeEach
    void setUp() throws Exception {
        S3Properties s3Properties = new S3Properties("facecook-photos", "ap-northeast-2", null, 300);
        service = new ProfilePhotoUploadService(s3Presigner, s3Properties, new ProfilePhotoUrlPolicy(s3Properties));
    }

    @Test
    void issuesUploadUrlForAllowedContentType() throws Exception {
        when(presignedRequest.url()).thenReturn(
                URI.create("https://facecook-photos.s3.ap-northeast-2.amazonaws.com/x?X-Amz-Signature=abc").toURL()
        );
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);

        PhotoUploadUrlResponse response = service.issueUploadUrl("image/jpeg");

        assertThat(response.uploadUrl()).contains("X-Amz-Signature");
        assertThat(response.photoUrl())
                .startsWith("https://facecook-photos.s3.ap-northeast-2.amazonaws.com/profile-photos/")
                .endsWith(".jpg");
    }

    @Test
    void generatesRandomKeyRatherThanUserIdOrSequentialValue() throws Exception {
        when(presignedRequest.url()).thenReturn(
                URI.create("https://facecook-photos.s3.ap-northeast-2.amazonaws.com/x").toURL()
        );
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);

        service.issueUploadUrl("image/png");
        service.issueUploadUrl("image/png");

        org.mockito.Mockito.verify(s3Presigner, org.mockito.Mockito.times(2))
                .presignPutObject(captor.capture());
        String firstKey = captor.getAllValues().get(0).putObjectRequest().key();
        String secondKey = captor.getAllValues().get(1).putObjectRequest().key();

        assertThat(firstKey).isNotEqualTo(secondKey);
        assertThat(firstKey).matches("profile-photos/[0-9a-f-]{36}\\.png");
    }

    @Test
    void rejectsUnsupportedContentType() {
        assertThatThrownBy(() -> service.issueUploadUrl("application/pdf"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION)
                );
    }

    @Test
    void setsContentTypeOnPutObjectRequest() throws Exception {
        when(presignedRequest.url()).thenReturn(
                URI.create("https://facecook-photos.s3.ap-northeast-2.amazonaws.com/x").toURL()
        );
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);

        service.issueUploadUrl("image/webp");

        org.mockito.Mockito.verify(s3Presigner).presignPutObject(captor.capture());
        PutObjectRequest putObjectRequest = captor.getValue().putObjectRequest();
        assertThat(putObjectRequest.contentType()).isEqualTo("image/webp");
        // 행사 종료와 함께 곧 지워지는 짧은 수명·자주 조회되는 데이터라
        // IA가 아니라 Standard가 더 싸다(별도 논의 반영).
        assertThat(putObjectRequest.storageClassAsString())
                .isEqualTo(software.amazon.awssdk.services.s3.model.StorageClass.STANDARD.toString());
        assertThat(putObjectRequest.bucket()).isEqualTo("facecook-photos");
    }
}
