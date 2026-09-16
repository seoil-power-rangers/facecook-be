package com.facecook.mission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "mission_template")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mission_template_id")
    private Long id;

    @Column(name = "step", nullable = false)
    private int step;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;
}
