package com.facecook.profile.dto;

import java.util.List;

/** 실제로 참가자가 갖고 있는 값만 담는다 — 아무도 없는 학과·MBTI를 골라 "0명"을 보는 일이 없게 한다. */
public record ProfileFiltersResponse(
        List<String> departments,
        List<String> mbtis,
        List<String> hobbies
) {
}
