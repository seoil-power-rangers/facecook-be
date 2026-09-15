package com.facecook.profile.repository;

import com.facecook.profile.entity.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    /**
     * ProfileActivityLookup이 profile.getUser()를 바로 읽으므로, User를
     * 나중에 각자 따로 불러오지 않도록(N+1) 목록/단건 조회 모두 미리
     * 조인해서 가져온다.
     */
    @EntityGraph(attributePaths = "user")
    @Override
    Optional<Profile> findById(Long userId);

    @EntityGraph(attributePaths = "user")
    @Override
    List<Profile> findAllById(Iterable<Long> userIds);

    @EntityGraph(attributePaths = "user")
    List<Profile> findAllByUserIdNotOrderByUserIdAsc(Long userId);

    @Query("SELECT COUNT(p) FROM Profile p WHERE p.user.lastActiveAt >= :since")
    long countActiveSince(@Param("since") LocalDateTime since);

    @Query("SELECT p FROM Profile p WHERE p.user.lastActiveAt >= :since")
    List<Profile> findAllActiveSince(@Param("since") LocalDateTime since);
}
