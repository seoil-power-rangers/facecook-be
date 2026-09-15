package com.facecook.profile.entity;

import com.facecook.auth.entity.User;
import com.facecook.profile.dto.CreateProfileRequest;
import com.facecook.profile.dto.UpdateProfileRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /**
     * user_id 컬럼 하나를 두 번 매핑한다 — 위 userId(@Id, insert 담당)와
     * 이 필드(읽기 전용, insertable/updatable=false)가 같은 컬럼을 가리킨다.
     * @MapsId 대신 이 방식을 쓴 이유는 Profile.create()가 이미 Long userId만
     * 받는 API라 호출부(회원가입 흐름)를 안 건드리기 위함이다.
     *
     * optional=false로 두면 Hibernate가 이 값이 항상 있다는 걸 알아서,
     * 바이트코드 강화 없이도 지연 로딩 프록시를 만들 수 있다 — 조회 시점에
     * User를 안 건드리면 실제 SELECT가 안 나간다. 목록처럼 여러 건을 한
     * 번에 가져올 땐 ProfileRepository의 @EntityGraph로 미리 조인해서
     * N+1을 피한다.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false, length = 10)
    private String gender;

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false, length = 4)
    private String mbti;

    @Column(nullable = false, length = 255)
    private String hobby;

    @Column(name = "blood_type", nullable = false, length = 5)
    private String bloodType;

    @Column(length = 100)
    private String department;

    @Column(length = 20)
    private String grade;

    @Column(length = 500)
    private String bio;

    @Column(name = "photo_url", length = 500)
    private String photo;

    @Column(name = "ideal_type", length = 255)
    private String idealType;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    private Profile(Long userId, CreateProfileRequest request) {
        this.userId = userId;
        this.nickname = request.nickname();
        this.gender = request.gender();
        this.age = request.age();
        this.mbti = request.mbti();
        this.hobby = request.hobby();
        this.bloodType = request.bloodType();
        this.department = request.department();
        this.grade = request.grade();
        this.bio = request.bio();
        this.photo = request.photo();
        this.idealType = request.idealType();
    }

    public static Profile create(Long userId, CreateProfileRequest request) {
        return new Profile(userId, request);
    }

    public void update(UpdateProfileRequest request) {
        if (request.department() != null) {
            this.department = request.department();
        }
        if (request.grade() != null) {
            this.grade = request.grade();
        }
        if (request.bio() != null) {
            this.bio = request.bio();
        }
        if (request.photo() != null) {
            this.photo = request.photo();
        }
    }
}
