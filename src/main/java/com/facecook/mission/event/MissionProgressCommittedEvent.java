package com.facecook.mission.event;

import com.facecook.mission.dto.MissionProgressResponse;

public record MissionProgressCommittedEvent(MissionProgressResponse progress) {
}
