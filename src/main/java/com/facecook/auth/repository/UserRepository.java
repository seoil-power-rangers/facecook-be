package com.facecook.auth.repository;

import com.facecook.auth.entity.User;
import com.facecook.auth.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
