package com.facecook.auth.repository;

import com.facecook.auth.entity.User;
import com.facecook.auth.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    long countByStatus(UserStatus status);

    long countByLastActiveAtGreaterThanEqualAndLastActiveAtLessThan(
            LocalDateTime startInclusive,
            LocalDateTime endExclusive
    );

    long countByLastActiveAtGreaterThanEqual(LocalDateTime startInclusive);

    /**
     * 컬럼 하나만 바꾸는 벌크 업데이트다 — 매 요청마다 엔티티를 통째로
     * 읽고 쓰면(findById 후 save) 낭비가 크다.
     */
    @Modifying
    @Query("UPDATE User u SET u.lastActiveAt = :now WHERE u.id = :id")
    void touchLastActiveAt(@Param("id") Long id, @Param("now") LocalDateTime now);
}
