package com.facecook.mission.event;

import com.facecook.mission.dto.MissionProgressResponse;

/**
 * "미션 진행이 바뀌었다"는 스프링 애플리케이션 이벤트. {@code MissionService#completeCurrentStep}이
 * {@code ApplicationEventPublisher}로 발행하고, {@link MissionProgressCommittedListener}가 받는다.
 * 이벤트로 나눈 이유는 리스너 설명 참고.
 */
public record MissionProgressCommittedEvent(MissionProgressResponse progress) {
}
