package com.facecook.profile.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.profile.dto.CreateProfileRequest;
import com.facecook.profile.dto.DepartmentGroupResponse;
import com.facecook.profile.dto.ProfileFiltersResponse;
import com.facecook.profile.dto.ProfileResponse;
import com.facecook.profile.dto.ProfileStatsResponse;
import com.facecook.profile.dto.UpdateProfileRequest;
import com.facecook.profile.entity.Profile;
import com.facecook.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final ProfilePhotoUrlPolicy photoUrlPolicy;
    private final ProfileActivityLookup activityLookup;

    @Transactional
    public ProfileResponse create(Long userId, CreateProfileRequest request) {
        if (profileRepository.existsById(userId)) {
            throw new ApiException(ErrorCode.PROFILE_ALREADY_EXISTS);
        }
        requireOwnPhotoUrlIfPresent(request.photo());
        requireValidDepartmentIfPresent(request.department());

        try {
            Profile saved = profileRepository.saveAndFlush(Profile.create(userId, request));
            // saveAndFlush 직후엔 user 연관이 비어 있다. toResponse()가
            // profile.getUser()를 읽으므로 @EntityGraph(user)로 다시 조회한다.
            Profile profile = profileRepository.findById(saved.getUserId())
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR));
            return activityLookup.toResponse(profile);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.PROFILE_ALREADY_EXISTS, exception);
        }
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(Long userId) {
        return activityLookup.toResponse(findProfile(userId));
    }

    @Transactional
    public ProfileResponse update(Long userId, UpdateProfileRequest request) {
        if (!request.hasChanges()) {
            throw new ApiException(ErrorCode.VALIDATION, "수정할 프로필 항목을 입력해주세요.");
        }
        requireOwnPhotoUrlIfPresent(request.photo());
        requireValidDepartmentIfPresent(request.department());

        Profile profile = findProfile(userId);
        profile.update(request);
        return activityLookup.toResponse(profile);
    }

    @Transactional(readOnly = true)
    public List<ProfileResponse> getParticipantsExcept(Long userId) {
        return activityLookup.toResponses(profileRepository.findAllByUserIdNotOrderByUserIdAsc(userId));
    }

    public List<DepartmentGroupResponse> getDepartments() {
        return DepartmentCatalog.groups().stream()
                .map(group -> new DepartmentGroupResponse(group.college(), group.majors()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfileStatsResponse getStats() {
        long total = profileRepository.count();
        long activeNow = profileRepository.countActiveSince(activityLookup.activeSince());
        return new ProfileStatsResponse(total, activeNow);
    }

    /**
     * 페이징이 아직 없어서(2순위 별도 작업) 지금은 전체를 기준으로 계산한다.
     * 참가자가 300명 수준이라 매번 전체를 훑어도 부담이 크지 않다.
     */
    @Transactional(readOnly = true)
    public ProfileFiltersResponse getFilters(boolean activeOnly) {
        List<Profile> profiles = activeOnly
                ? profileRepository.findAllActiveSince(activityLookup.activeSince())
                : profileRepository.findAll();

        TreeSet<String> departments = new TreeSet<>();
        TreeSet<String> mbtis = new TreeSet<>();
        TreeSet<String> hobbies = new TreeSet<>();

        for (Profile profile : profiles) {
            if (profile.getDepartment() != null && !profile.getDepartment().isBlank()) {
                departments.add(profile.getDepartment());
            }
            if (profile.getMbti() != null && !profile.getMbti().isBlank()) {
                mbtis.add(profile.getMbti());
            }
            for (String hobby : profile.getHobby().split(",")) {
                String trimmed = hobby.trim();
                if (!trimmed.isEmpty()) {
                    hobbies.add(trimmed);
                }
            }
        }

        return new ProfileFiltersResponse(
                new ArrayList<>(departments),
                new ArrayList<>(mbtis),
                new ArrayList<>(hobbies)
        );
    }

    private Profile findProfile(Long userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.PROFILE_NOT_FOUND));
    }

    /** photo 필드에 우리 업로드 API가 내준 게 아닌 임의의 URL이 그대로 저장되지 않게 막는다. */
    private void requireOwnPhotoUrlIfPresent(String photo) {
        if (photo != null && !photo.isBlank() && !photoUrlPolicy.isOwnPhotoUrl(photo)) {
            throw new ApiException(ErrorCode.VALIDATION, "올바르지 않은 사진 주소입니다.");
        }
    }

    /**
     * 정해진 30개 중 하나만 허용한다 — 자유 입력을 그대로 두면 "컴공"·
     * "컴퓨터공학과"처럼 표기가 갈려서 탐색 화면의 학과 필터가 문자열
     * 비교에서 못 걸러낸다.
     */
    private void requireValidDepartmentIfPresent(String department) {
        if (department != null && !department.isBlank()
                && !DepartmentCatalog.VALID_DEPARTMENTS.contains(department)) {
            throw new ApiException(ErrorCode.VALIDATION, "올바르지 않은 학과입니다.");
        }
    }
}
