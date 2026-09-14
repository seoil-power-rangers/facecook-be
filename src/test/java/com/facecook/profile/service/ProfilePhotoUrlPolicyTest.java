package com.facecook.profile.service;

import com.facecook.config.S3Properties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilePhotoUrlPolicyTest {

    @Test
    void buildsVirtualHostedUrlWhenPublicBaseUrlNotConfigured() {
        ProfilePhotoUrlPolicy policy = new ProfilePhotoUrlPolicy(
                new S3Properties("facecook-photos", "ap-northeast-2", null, 300)
        );

        String url = policy.urlFor("profile-photos/abc.jpg");

        assertThat(url).isEqualTo(
                "https://facecook-photos.s3.ap-northeast-2.amazonaws.com/profile-photos/abc.jpg"
        );
        assertThat(policy.isOwnPhotoUrl(url)).isTrue();
    }

    @Test
    void prefersConfiguredPublicBaseUrlOverVirtualHostedUrl() {
        ProfilePhotoUrlPolicy policy = new ProfilePhotoUrlPolicy(
                new S3Properties("facecook-photos", "ap-northeast-2", "https://cdn.devseok.xyz/", 300)
        );

        String url = policy.urlFor("profile-photos/abc.jpg");

        assertThat(url).isEqualTo("https://cdn.devseok.xyz/profile-photos/abc.jpg");
        assertThat(policy.isOwnPhotoUrl(url)).isTrue();
    }

    @Test
    void rejectsUrlFromAnotherDomain() {
        ProfilePhotoUrlPolicy policy = new ProfilePhotoUrlPolicy(
                new S3Properties("facecook-photos", "ap-northeast-2", null, 300)
        );

        assertThat(policy.isOwnPhotoUrl("https://evil.example.com/profile-photos/abc.jpg")).isFalse();
    }
}
