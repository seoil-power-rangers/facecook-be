package com.facecook.profile.dto;

import java.util.List;

/** {@code GET /api/departments}의 한 항목: 학부 이름과 그 아래 학과 목록({@code DepartmentCatalog}에서 옴). */
public record DepartmentGroupResponse(String college, List<String> majors) {
}
