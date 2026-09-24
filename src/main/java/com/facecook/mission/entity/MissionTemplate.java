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

/**
 * 미션 문구 원본({@code mission_template}). V6 마이그레이션이 묶음(bundle) 단위로 넣어 둔 고정 데이터이고,
 * 코드는 읽기만 한다.
 */
@Getter
@Entity
@Table(name = "mission_template")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mission_template_id")
    private Long id;

    /**
     * 이 미션이 속한 묶음. 같은 묶음의 STEP1~3은 한 세트로 만들어졌고, 한 매칭에는 묶음 하나가 통째로
     * 배정된다({@link com.facecook.mission.service.MissionAssignmentWriter} 참고).
     */
    @Column(name = "bundle_id", nullable = false)
    private Long bundleId;

    @Column(name = "step", nullable = false)
    private int step;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;
}
