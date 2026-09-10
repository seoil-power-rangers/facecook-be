# facecook-be — 에이전트 작업 지침

이 문서는 AI 에이전트(Claude 등)가 이 레포에서 작업할 때 참고하는 진입점이다.

## 1. 이 레포가 뭔지

`face 콕`(제52회 용마대동제 소개팅 부스) 서비스의 **백엔드 전용** 레포다.
Java 21 + Spring Boot(Gradle) 기반이다. 화면(UI)은 `facecook-fe`(Next.js)에
있다.

**작업 전에 [CONTRIBUTING.md](./CONTRIBUTING.md)와 [README.md](./README.md)를
먼저 읽는다.**

## 2. 경계

- UI/화면 관련 코드를 이 레포에 만들지 않는다. `facecook-fe`의 책임이다
- API·DB 스키마는 팀이 정리해둔 API명세.md, ERD.md 기준으로 한다 — 임의로
  바꾸지 않고, 바뀌면 그 문서부터 갱신하고 프론트와 공유한다

## 3. 패키지 구조

기능별(도메인별) 패키지로 나눈다. 레이어별(controller/service 전체 뭉치)로
나누지 않는다.

```text
com.facecook
├── config/     Security, WebSocket(STOMP), Redis, 세션 설정
├── auth/       로그인/회원가입, 세션
├── profile/    프로필
├── cook/       콕찔러보기, 매칭 성사
├── chat/       채팅 (REST + STOMP)
├── mission/    미션 STEP
├── report/     신고/영구정지
├── admin/      관리자 통계 (다른 도메인 데이터를 읽기만 함)
├── push/       웹 푸시 구독
└── common/     공통 응답 포맷, 예외 처리, 세션 유틸
```

각 도메인 패키지 안에 `Controller`, `Service`, `Repository`, `Entity`,
`dto/`를 둔다.

## 4. DB / 마이그레이션

- Flyway로 스키마를 관리한다. `src/main/resources/db/migration/`에 버전
  파일을 추가하는 방식이고, 기존 파일은 수정하지 않는다
- JPA `ddl-auto`는 `validate`로 고정한다 — 스키마 변경은 항상 Flyway를 거친다

## 5. 실행

```bash
docker compose up -d
./gradlew bootRun
```

빌드/테스트: `./gradlew build`

## 6. Git 커밋 규칙

- 브랜치·커밋 타입·PR 규칙은 [CONTRIBUTING.md](./CONTRIBUTING.md)를 따른다
- **커밋 메시지에 `Co-Authored-By: Claude` 트레일러를 붙이지 않는다.** 이
  레포 팀 컨벤션이다
