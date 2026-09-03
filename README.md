# face 콕 — Backend

제52회 용마대동제 소개팅 부스 웹서비스의 **백엔드 전용** 레포입니다.
프론트는 [`facecook-fe`](https://github.com/seoil-power-rangers/facecook-fe)
(Next.js)로 분리되어 있습니다.

---

## 실행

```bash
docker compose up -d
./gradlew bootRun
```

http://localhost:8080

Docker Compose가 MySQL 8.0 · Redis 7을 로컬에 띄웁니다. 실행 시 Flyway가
`src/main/resources/db/migration/`의 마이그레이션을 자동 적용합니다.

환경변수는 `.env.example`을 참고해서 로컬용 값을 채웁니다 (실제 값은 이
파일에 적지 않습니다).

---

## 기술 스택

- Java 21, Spring Boot, Gradle(Groovy DSL)
- MySQL 8.0(RDS), Redis 7(ElastiCache) — 실시간 채팅용 Pub/Sub
- WebSocket(STOMP) — 채팅 실시간 처리
- Flyway — DB 스키마 버전 관리

---

## 폴더 구조

```text
src/main/java/com/facecook/
├── config/     Security, WebSocket(STOMP), Redis, 세션 설정
├── auth/       로그인/회원가입, 세션
├── profile/    프로필
├── cook/       콕찔러보기, 매칭 성사
├── chat/       채팅 (REST + STOMP)
├── mission/    미션 STEP
├── report/     신고/영구정지
├── admin/      관리자 통계
├── push/       웹 푸시 구독
└── common/     공통 응답 포맷, 예외 처리, 세션 유틸

src/main/resources/
├── application.yml
└── db/migration/   Flyway 마이그레이션
```

패키지는 레이어별이 아니라 **기능별**로 나뉩니다. 각 도메인 패키지 안에
Controller/Service/Repository/Entity/dto가 들어갑니다.

---

## 범위

REST API, WebSocket, DB 접근, 인증/세션, 외부 연동(이메일, 웹 푸시, S3)을
담당합니다. 화면(UI)은 `facecook-fe`의 책임이고 이 레포에 두지 않습니다.

작업 방식은 [CONTRIBUTING.md](./CONTRIBUTING.md)를 따릅니다.
