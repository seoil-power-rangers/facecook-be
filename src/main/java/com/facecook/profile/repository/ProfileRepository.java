package com.facecook.profile.repository;

import com.facecook.profile.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    List<Profile> findAllByUserIdNotOrderByUserIdAsc(Long userId);
}
