package com.facecook.profile.controller;

import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import com.facecook.profile.dto.CreateProfileRequest;
import com.facecook.profile.dto.DepartmentGroupResponse;
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

/**
 * 프로필 API. 내 프로필 작성·조회·수정, 다른 참가자 목록·상세, 탐색 필터, 통계, 학과 목록.
 *
 * <table>
 * <tr><th>API</th><th>서비스</th><th>쓰는 화면(FE)</th></tr>
 * <tr><td>POST /api/profile/photo/upload-url</td><td>{@link ProfilePhotoUploadService#issueUploadUrl}</td><td>프로필 작성·수정(사진)</td></tr>
 * <tr><td>POST /api/profile</td><td>{@link ProfileService#create}</td><td>온보딩 마지막 단계</td></tr>
 * <tr><td>GET /api/profile</td><td>{@link ProfileService#get}(내 userId)</td><td>메인·마이페이지, 온보딩 여부 확인</td></tr>
 * <tr><td>PATCH /api/profile</td><td>{@link ProfileService#update}</td><td>마이페이지 수정</td></tr>
 * <tr><td>GET /api/profiles</td><td>{@link ProfileService#getParticipantsExcept}</td><td>탐색</td></tr>
 * <tr><td>GET /api/profiles/filters</td><td>{@link ProfileService#getFilters}</td><td>탐색 필터</td></tr>
 * <tr><td>GET /api/profiles/{userId}</td><td>{@link ProfileService#get}</td><td>프로필 상세</td></tr>
 * <tr><td>GET /api/stats</td><td>{@link ProfileService#getStats}</td><td>메인 "총 사용자"</td></tr>
 * <tr><td>GET /api/departments</td><td>{@link ProfileService#getDepartments}</td><td>학과 선택</td></tr>
 * </table>
 *
 * <p>모두 {@code /api/**}라 로그인해야 부를 수 있다({@code SessionAuthenticationInterceptor}).
 * 몇몇 메서드는 {@code @CurrentUser}를 받기만 하고 쓰지 않는데, 로그인 확인은 인터셉터가
 * 이미 했으므로 이 파라미터가 없어도 동작은 같다({@code /api/departments}가 그 예).</p>
 *
 * <p>내 프로필을 바꾸는 API는 경로에 userId를 받지 않고 세션의 userId만 쓴다 — 남의
 * 프로필을 고치는 요청을 만들 방법 자체가 없다.</p>
 */
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

    @GetMapping("/departments")
    public ResponseEntity<List<DepartmentGroupResponse>> getDepartments() {
        return ResponseEntity.ok(profileService.getDepartments());
    }
}
