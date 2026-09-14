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
     *
     * WHERE 절에 디바운스 조건(staleBefore)을 같이 걸어서, 이미 최근에
     * 갱신된 행이면 이 UPDATE 자체가 매치되는 행이 없어 아무 일도 안
     * 일어난다 — 매번 값을 먼저 읽어서 비교할 필요가 없다. 프론트가
     * 5초 간격으로 폴링하는 화면이 많아서, 디바운스가 없으면 인증된
     * 요청마다 매번 이 테이블에 쓰기가 발생한다.
     */
    @Modifying
    @Query("UPDATE User u SET u.lastActiveAt = :now WHERE u.id = :id AND (u.lastActiveAt IS NULL OR u.lastActiveAt < :staleBefore)")
    void touchLastActiveAt(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore
    );
}
