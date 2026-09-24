package com.facecook.profile.repository;

import com.facecook.profile.entity.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@code profile} 테이블 조회·저장. 읽는 법은 {@code UserRepository} 설명과 같다.
 *
 * <p>{@code @EntityGraph(attributePaths = "user")}: 프로필을 가져올 때 연결된
 * {@code User}도 같은 SQL(JOIN)로 함께 가져오라는 뜻이다. 목록 300건을 가져온 뒤
 * 건마다 {@code users}를 따로 조회하는 N+1(쿼리 1 + N번) 문제를 막는다.</p>
 *
 * <p>{@code p.user.lastActiveAt}처럼 JPQL에서 연결된 엔티티의 필드를 쓰면 JPA가
 * {@code users}와 조인한 SQL로 바꿔 준다.</p>
 */
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
