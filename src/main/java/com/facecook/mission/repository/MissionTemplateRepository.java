package com.facecook.mission.repository;

import com.facecook.mission.entity.MissionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MissionTemplateRepository extends JpaRepository<MissionTemplate, Long> {
    List<MissionTemplate> findAllByStep(int step);
}
