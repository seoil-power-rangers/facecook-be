package com.facecook.profile.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 학과 정본(학부 7개 × 학과 30개)의 단일 소스. GET /api/departments로 그대로
 * 노출돼 facecook-fe의 DepartmentPicker가 이 값을 받아 쓴다 — 예전엔 이 목록이
 * FE ui/공통/constants.ts COLLEGES에도 똑같이 하드코딩돼 있어서 한쪽만
 * 바뀌면 조용히 어긋났다.
 *
 * 자유 입력이던 시절 가입자에게 남은 값("컴공" 등)은 이 목록에 없어도
 * 저장은 이미 돼 있는 채로 남는다 — 여기서는 "새로 들어오는 값"만 막는다.
 */
public final class DepartmentCatalog {

    public record DepartmentGroup(String college, List<String> majors) {
    }

    private static final List<DepartmentGroup> GROUPS = List.of(
            new DepartmentGroup("IT융합학부", List.of(
                    "IoT전자공학과", "전기공학과", "정보통신공학과", "소프트웨어공학과",
                    "디지털윈엘리베이터학과", "AI게임융합학과", "글로벌AI융합학과"
            )),
            new DepartmentGroup("스마트공학부", List.of(
                    "건축과", "생명화학공학과", "건설시스템공학과", "스마트자동차공학과"
            )),
            new DepartmentGroup("휴먼케어학부", List.of(
                    "간호학과", "유아교육학과", "식품영양학과", "사회복지학과", "스포츠헬스케어학과"
            )),
            new DepartmentGroup("글로벌외국어학부", List.of(
                    "비즈니스영어과", "비즈니스일본어과", "비즈니스중국어과"
            )),
            new DepartmentGroup("경영사회학부", List.of(
                    "스마트경영학과", "부동산법률학과", "미디어출판학과", "세무회계학과"
            )),
            new DepartmentGroup("디자인학부", List.of(
                    "커뮤니케이션디자인학과", "패션산업학과", "생활가구디자인학과", "실내디자인학과", "VMD&전시디자인학과"
            )),
            new DepartmentGroup("미디어예술학부", List.of(
                    "영화방송공연예술학과", "만화웹툰학과"
            ))
    );

    static final Set<String> VALID_DEPARTMENTS = GROUPS.stream()
            .flatMap(group -> group.majors().stream())
            .collect(Collectors.toUnmodifiableSet());

    public static List<DepartmentGroup> groups() {
        return GROUPS;
    }

    private DepartmentCatalog() {
    }
}
