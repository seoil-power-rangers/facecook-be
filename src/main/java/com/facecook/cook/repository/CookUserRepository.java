package com.facecook.cook.repository;

import com.facecook.auth.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 콕 처리 중에 두 사용자 행({@code users})을 잠그는 전용 저장소. {@code CookService}만 쓴다.
 *
 * <p>{@code @Lock(PESSIMISTIC_WRITE)}은 {@code SELECT ... FOR UPDATE}를 만든다. 같은 두 사람에 대한 콕
 * 명령(보내기·취소·거절)이 동시에 오면 나중 요청은 앞 요청의 트랜잭션이 끝날 때까지 기다렸다가, 앞 요청이
 * 남긴 최신 상태를 보고 판정한다(예: 취소와 거절이 겹쳐도 하나만 성공, 거절 직후 온 역방향 전송은 막힘 —
 * {@code CookLockingIntegrationTest}).</p>
 *
 * <p>{@code order by user.id}: 항상 작은 ID부터 잠근다. 두 요청이 서로 반대 순서로 잠그면 서로를 영원히
 * 기다리는 교착(deadlock)이 생길 수 있어서, 순서를 하나로 고정했다.</p>
 *
 * <p>{@code JpaRepository}가 아니라 빈 {@code Repository}를 상속해서, {@code save}·{@code delete} 같은
 * 기본 메서드 없이 여기 선언한 잠금 조회 하나만 연다.</p>
 */
public interface CookUserRepository extends Repository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id in :userIds order by user.id")
    List<User> findAllByIdForUpdate(@Param("userIds") Collection<Long> userIds);
}
