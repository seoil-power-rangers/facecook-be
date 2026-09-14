package com.facecook.profile.controller;

import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import com.facecook.profile.dto.CreateProfileRequest;
import com.facecook.profile.dto.PhotoUploadUrlRequest;
import com.facecook.profile.dto.PhotoUploadUrlResponse;
import com.facecook.profile.dto.ProfileFiltersResponse;
import com.facecook.profile.dto.ProfileResponse;
import com.facecook.profile.dto.ProfileStatsResponse;
import com.facecook.profile.dto.UpdateProfileRequest;
import com.facecook.profile.service.ProfilePhotoUploadService;
import com.facecook.profile.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ProfileController {

    private final ProfileService profileService;
    private final ProfilePhotoUploadService profilePhotoUploadService;

    @PostMapping("/profile/photo/upload-url")
    public ResponseEntity<PhotoUploadUrlResponse> issuePhotoUploadUrl(
            @CurrentUser AuthenticatedUser currentUser,
            @Valid @RequestBody PhotoUploadUrlRequest request
    ) {
        return ResponseEntity.ok(profilePhotoUploadService.issueUploadUrl(request.contentType()));
    }

    @PostMapping("/profile")
    public ResponseEntity<ProfileResponse> create(
            @CurrentUser AuthenticatedUser currentUser,
            @Valid @RequestBody CreateProfileRequest request
    ) {
        return ResponseEntity.ok(profileService.create(currentUser.userId(), request));
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> getMine(@CurrentUser AuthenticatedUser currentUser) {
        return ResponseEntity.ok(profileService.get(currentUser.userId()));
    }

    @PatchMapping("/profile")
    public ResponseEntity<ProfileResponse> update(
            @CurrentUser AuthenticatedUser currentUser,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(profileService.update(currentUser.userId(), request));
    }

    @GetMapping("/profiles")
    public ResponseEntity<List<ProfileResponse>> getParticipants(
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ResponseEntity.ok(profileService.getParticipantsExcept(currentUser.userId()));
    }

    @GetMapping("/profiles/filters")
    public ResponseEntity<ProfileFiltersResponse> getFilters(
            @CurrentUser AuthenticatedUser currentUser,
            // boolean으로 받으면 ?active=(빈 값)처럼 파라미터는 있는데 값이 빈
            // 문자열일 때 defaultValue가 적용되지 않아 타입 변환 예외가 난다.
            // String으로 받고 Boolean.parseBoolean으로 직접 판단하면(빈 값·
            // "true" 외 어떤 값이든 false로 취급) 예외 없이 항상 처리된다.
            @RequestParam(defaultValue = "false") String active
    ) {
        return ResponseEntity.ok(profileService.getFilters(Boolean.parseBoolean(active)));
    }

    @GetMapping("/profiles/{userId}")
    public ResponseEntity<ProfileResponse> getParticipant(
            @CurrentUser AuthenticatedUser currentUser,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(profileService.get(userId));
    }

    @GetMapping("/stats")
    public ResponseEntity<ProfileStatsResponse> getStats(@CurrentUser AuthenticatedUser currentUser) {
        return ResponseEntity.ok(profileService.getStats());
    }
}
