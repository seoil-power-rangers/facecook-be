package com.facecook.profile.entity;

import com.facecook.auth.entity.User;
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

    private Profile(Long userId, NewProfile input) {
        this.userId = userId;
        this.nickname = input.nickname();
        this.gender = input.gender();
        this.age = input.age();
        this.mbti = input.mbti();
        this.hobby = input.hobby();
        this.bloodType = input.bloodType();
        this.department = input.department();
        this.grade = input.grade();
        this.bio = input.bio();
        this.photo = input.photo();
        this.idealType = input.idealType();
    }

    public static Profile create(Long userId, NewProfile input) {
        return new Profile(userId, input);
    }

    /**
     * 넘어온 필드만 바꾼다 — 각 필드는 {@code null}이면 그대로 두고, 빈 문자열이면
     * 지운다(작성자가 실제로 지우려는 것과 "안 건드림"을 구분해야 하는 부분 수정
     * API라 이 둘을 섞으면 안 된다).
     */
    public void update(Edit edit) {
        if (edit.department() != null) {
            this.department = edit.department();
        }
        if (edit.grade() != null) {
            this.grade = edit.grade();
        }
        if (edit.bio() != null) {
            this.bio = edit.bio();
        }
        if (edit.photo() != null) {
            this.photo = edit.photo();
        }
    }

    /**
     * {@link #create}가 받는 도메인 입력. 요청 DTO({@code CreateProfileRequest})를
     * 그대로 받지 않는 이유는, API 요청 형식이 바뀔 때마다 엔티티까지 따라 바뀌지
     * 않게 하기 위해서다 — DTO→이 값 변환은 {@code ProfileService}가 한다.
     */
    public record NewProfile(
            String nickname, String gender, Integer age, String mbti, String hobby,
            String bloodType, String department, String grade, String bio,
            String idealType, String photo
    ) {
    }

    /**
     * {@link #update}가 받는 도메인 입력. {@code CreateProfileRequest}와 마찬가지로
     * {@code UpdateProfileRequest}를 직접 받지 않는다. 필드가 {@code null}이면
     * "이 필드는 안 바꾼다"는 뜻이고, 이 의미는 변환하는 쪽({@code ProfileService})이
     * 아니라 이 값을 적용하는 {@link #update}가 그대로 유지한다.
     */
    public record Edit(String department, String grade, String bio, String photo) {
    }
}
