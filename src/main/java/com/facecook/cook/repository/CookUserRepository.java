package com.facecook.cook.repository;

import com.facecook.auth.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CookUserRepository extends Repository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id in :userIds order by user.id")
    List<User> findAllByIdForUpdate(@Param("userIds") Collection<Long> userIds);
}
