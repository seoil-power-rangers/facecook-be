package com.facecook.profile.repository;

import com.facecook.profile.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    List<Profile> findAllByUserIdNotOrderByUserIdAsc(Long userId);

    /**
     * Profile은 User를 참조하는 JPA 연관관계가 없어(별도 테이블, userId만
     * 공유) 명시적 ON 조건으로 조인한다.
     */
    @Query("SELECT COUNT(p) FROM Profile p JOIN User u ON u.id = p.userId WHERE u.lastActiveAt >= :since")
    long countActiveSince(@Param("since") LocalDateTime since);

    @Query("SELECT p FROM Profile p JOIN User u ON u.id = p.userId WHERE u.lastActiveAt >= :since")
    List<Profile> findAllActiveSince(@Param("since") LocalDateTime since);
}
