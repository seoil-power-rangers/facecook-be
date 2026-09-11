package com.facecook.profile.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.profile.dto.CreateProfileRequest;
import com.facecook.profile.dto.ProfileResponse;
import com.facecook.profile.dto.UpdateProfileRequest;
import com.facecook.profile.entity.Profile;
import com.facecook.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;

    @Transactional
    public ProfileResponse create(Long userId, CreateProfileRequest request) {
        if (profileRepository.existsById(userId)) {
            throw new ApiException(ErrorCode.PROFILE_ALREADY_EXISTS);
        }

        try {
            Profile profile = profileRepository.saveAndFlush(Profile.create(userId, request));
            return ProfileResponse.from(profile);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.PROFILE_ALREADY_EXISTS, exception);
        }
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(Long userId) {
        return ProfileResponse.from(findProfile(userId));
    }

    @Transactional
    public ProfileResponse update(Long userId, UpdateProfileRequest request) {
        if (!request.hasChanges()) {
            throw new ApiException(ErrorCode.VALIDATION, "수정할 프로필 항목을 입력해주세요.");
        }

        Profile profile = findProfile(userId);
        profile.update(request);
        return ProfileResponse.from(profile);
    }

    @Transactional(readOnly = true)
    public List<ProfileResponse> getParticipantsExcept(Long userId) {
        return profileRepository.findAllByUserIdNotOrderByUserIdAsc(userId).stream()
                .map(ProfileResponse::from)
                .toList();
    }

    private Profile findProfile(Long userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.PROFILE_NOT_FOUND));
    }
}
