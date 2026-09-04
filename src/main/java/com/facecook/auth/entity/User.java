package com.facecook.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Convert(converter = UserRoleConverter.class)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Convert(converter = UserStatusConverter.class)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "agreed_privacy_at")
    private LocalDateTime agreedPrivacyAt;

    @Column(name = "agreed_terms_at")
    private LocalDateTime agreedTermsAt;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private User(
            String email,
            UserRole role,
            UserStatus status,
            LocalDateTime agreedPrivacyAt,
            LocalDateTime agreedTermsAt,
            LocalDateTime createdAt
    ) {
        this.email = email;
        this.role = role;
        this.status = status;
        this.agreedPrivacyAt = agreedPrivacyAt;
        this.agreedTermsAt = agreedTermsAt;
        this.createdAt = createdAt;
    }

    public static User createParticipant(String email, LocalDateTime agreedAt) {
        return new User(
                email,
                UserRole.PARTICIPANT,
                UserStatus.ACTIVE,
                agreedAt,
                agreedAt,
                agreedAt
        );
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }
}
