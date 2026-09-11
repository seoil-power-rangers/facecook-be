package com.facecook.profile.entity;

import com.facecook.profile.dto.CreateProfileRequest;
import com.facecook.profile.dto.UpdateProfileRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
