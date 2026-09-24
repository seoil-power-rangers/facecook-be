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

/**
 * 계정({@code users} 테이블). 참가자·관리자·슈퍼 계정이 모두 여기 있고 {@link #role}로
 * 구분한다. 프로필(닉네임·MBTI 등)은 별도 테이블 {@code profile}에 있다.
 *
 * <p>JPA 엔티티 읽는 법: {@code @Entity}가 붙은 클래스의 필드가 테이블 컬럼과 1:1로
 * 연결된다. {@code @Id}+{@code @GeneratedValue(IDENTITY)}는 DB의 AUTO_INCREMENT로 ID를
 * 받는다는 뜻이다. 트랜잭션 안에서 조회한 엔티티의 필드를 바꾸면 커밋할 때 UPDATE가
 * 자동으로 나간다(따로 save를 안 불러도 된다).</p>
 *
 * <p>setter를 열어 두지 않은 이유: 아무 곳에서나 상태를 바꾸지 못하게, 바뀔 수 있는
 * 동작({@link #suspend}, {@link #setPassword})만 메서드로 연다. 인자 없는
 * {@code protected} 생성자({@code @NoArgsConstructor})는 JPA가 조회 결과로 객체를
 * 만들 때 필요해서 두고, 코드에서는 {@link #createParticipant}·{@link #createSuper}로만 만든다.</p>
 *
 * <p>{@code lastActiveAt}은 이 엔티티로 바꾸지 않는다 — 요청마다 갱신돼서
 * {@code UserRepository#touchLastActiveAt}이 컬럼 하나만 직접 UPDATE한다.</p>
 */
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

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

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

    /** 이메일 인증을 마친 참가자 계정. 약관 동의 시각과 가입 시각을 같은 값(가입 순간, 한국 시간)으로 둔다. */
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

    /** 슈퍼 계정. 환경변수로 받은 아이디로 서버 기동 시 {@code SuperAccountBootstrap}이 만든다. 약관 동의는 없다. */
    public static User createSuper(String login, LocalDateTime createdAt) {
        return new User(
                login,
                UserRole.SUPER,
                UserStatus.ACTIVE,
                null,
                null,
                createdAt
        );
    }

    /** 계정 정지. 신고 처리({@code ReportService})에서 부른다. 다음 요청부터 세션 확인 단계에서 막힌다. */
    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    /** 비밀번호 해시(평문 아님)를 저장한다. 해시는 호출부가 {@code PasswordEncoder}로 만들어 넘긴다. */
    public void setPassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
