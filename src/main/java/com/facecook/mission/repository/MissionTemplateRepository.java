package com.facecook.mission.repository;

import com.facecook.mission.entity.MissionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MissionTemplateRepository extends JpaRepository<MissionTemplate, Long> {

    /** 존재하는 묶음 ID 전체. 매칭에 배정할 묶음을 무작위로 고를 때 후보 목록으로 쓴다. */
    @Query("select distinct t.bundleId from MissionTemplate t")
    List<Long> findDistinctBundleIds();

    Optional<MissionTemplate> findByBundleIdAndStep(Long bundleId, int step);
}
