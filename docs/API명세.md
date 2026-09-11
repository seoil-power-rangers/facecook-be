# face 콕 — API 명세

정리 기준일: 2026-09-03

- 인증: 세션 쿠키(HttpOnly) 기반. `[참가자]` = 로그인한 참가자 세션 필요, `[관리자]` = 관리자 세션 필요, `[공개]` = 인증 불필요
- 응답 포맷: JSON, 실패 시 `{ "code": "ERROR_CODE", "message": "..." }`
- 정확한 필드 타입은 ERD 확정 후 갱신

## 1. 로그인 / 회원가입

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/auth/request-code` | 이메일 인증코드 발급 (`purpose: signup\|login`) | 공개 |
| POST | `/api/auth/verify-signup` | 인증코드 확인 + 회원가입 (`email, code, agreedTerms[]`) | 공개 |
| POST | `/api/auth/verify-login` | 인증코드 확인 + 로그인 | 공개 |
| POST | `/api/auth/admin-login` | 관리자 로그인 (`adminId, password`) | 공개 |
| GET | `/api/auth/me` | 현재 세션 정보 조회 | 참가자/관리자 |
| POST | `/api/auth/logout` | 로그아웃 | 참가자/관리자 |

### 이메일 인증 요청/응답

`POST /api/auth/request-code`

```json
{
  "email": "user@example.com",
  "purpose": "signup"
}
```

- `purpose`: `signup` 또는 `login`
- 성공 응답:

```json
{
  "expiresInSeconds": 300,
  "resendAfterSeconds": 30
}
```

### 회원가입 인증 요청/응답

`POST /api/auth/verify-signup`

```json
{
  "email": "user@example.com",
  "code": "123456",
  "agreedTerms": ["service", "privacy"]
}
```

`agreedTerms` 값:

| ID | 필수 여부 | 설명 |
| --- | --- | --- |
| `service` | 필수 | 서비스 이용약관 |
| `privacy` | 필수 | 개인정보 수집·이용 동의 |
| `photo` | 선택 | 프로필 사진 업로드 동의 |

### 로그인 인증 요청/응답

`POST /api/auth/verify-login`

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

`verify-signup`, `verify-login` 성공 응답:

```json
{
  "userId": 1,
  "email": "user@example.com",
  "role": "participant"
}
```

세션 쿠키 발급은 로그인/세션 구현 시 위 응답과 함께 추가한다.

## 2. 프로필

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/profile` | 내 프로필 조회 | 참가자 |
| POST | `/api/profile` | 필수+선택 프로필 최초 등록 (`nickname, gender, age, mbti, hobby, bloodType, department?, grade?, bio?, idealType?, photo?`) | 참가자 |
| PATCH | `/api/profile` | 선택 항목만 수정 (`department?, grade?, bio?, photo?`) — 필수 필드는 요청 자체에 안 받음 | 참가자 |
| GET | `/api/profiles` | 참가자 목록 (본인 제외) | 참가자 |
| GET | `/api/profiles/{userId}` | 특정 참가자 프로필 상세 | 참가자 |

## 3. 콕찔러보기 / 매칭

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/cooks` | 콕 보내기 (`receiverId`) → 응답에 매칭 성사 여부 포함 | 참가자 |
| GET | `/api/cooks` | 보낸/받은 콕 목록 + 오늘/전체 사용량 통계 | 참가자 |
| GET | `/api/matches` | 내 매칭 목록 (상대 프로필 + 최근 메시지 미리보기) | 참가자 |
| GET | `/api/matches/{matchId}` | 매칭 상세 | 참가자 |

**콕 보내기 실패 코드**: `SELF`(자기자신), `NOT_FOUND`(대상없음), `ALREADY_MATCHED`, `DUPLICATE`, `DAILY_LIMIT`, `EVENT_LIMIT`

## 4. 채팅

### REST

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/matches/{matchId}/messages?before={messageId}&limit=50` | 메시지 히스토리 조회 (페이지네이션) | 참가자(해당 매칭 당사자만) |

- 응답은 `messageId` 내림차순 배열이며, 다음 페이지는 마지막 항목의
  `messageId`를 `before`로 보낸다. `limit` 기본값은 50, 허용 범위는 1~100이다.
- 메시지 항목: `{ messageId, matchId, senderId, content, clientMessageId, sentAt }`

### WebSocket (STOMP)

| 구분 | 목적지 | 설명 |
| --- | --- | --- |
| CONNECT | `/ws` | 세션 쿠키로 인증, 연결 시 Redis 접속자 명단에 등록 |
| SUBSCRIBE | `/topic/chat/{matchId}` | 해당 채팅방 메시지 실시간 수신 — **구독 시점에 이 matchId 당사자인지 서버가 검증** |
| SEND | `/app/chat/{matchId}/send` | 메시지 전송, body: `{ content, clientMessageId }` |
| SUBSCRIBE | `/user/queue/chat-acks` | DB 저장이 끝난 SEND 결과 수신. 재전송이면 기존 메시지를 동일한 형식으로 반환 |
| — | — | 운영시간(09:00~18:00) 외 전송 시 `CLOSED` 에러 반환 |
| DISCONNECT | — | 연결 종료 시 Redis 접속자 명단에서 제거 |

`/topic/chat/{matchId}`와 `/user/queue/chat-acks`의 메시지 형식은 REST 메시지
항목과 같다. STOMP 처리 실패는 ERROR frame의 JSON body
`{ "code": "ERROR_CODE", "message": "..." }`로 반환한다.

## 5. 미션

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/matches/{matchId}/mission` | 미션 진행상황 조회 (`currentStep`, STEP별 완료시각) | 참가자(해당 매칭 당사자만) |
| GET | `/api/admin/missions` | 전체 매칭의 미션 진행 현황 목록 | 관리자 |
| POST | `/api/admin/missions/{matchId}/complete` | 현재 STEP 완료 처리 → 다음 STEP 공개 | 관리자 |

## 6. 신고

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/reports` | 신고 접수 (`reportedUserId, reason, detail?`) | 참가자 |
| GET | `/api/admin/reports` | 신고 목록 조회 | 관리자 |
| GET | `/api/admin/reports/{reportId}` | 신고 상세 | 관리자 |
| POST | `/api/admin/reports/{reportId}/resolve` | 신고 처리 (`suspend: boolean`) | 관리자 |
| GET | `/api/admin/reports/{reportId}/chat` | 신고 관련 채팅 열람 | 관리자 |

## 7. 관리자 통계

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/admin/stats` | 가입자수/활성사용자/콕사용량/매칭수/미션완료수/대기신고수 | 관리자 |

## 8. 알림 (웹 푸시)

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/push/subscribe` | 브라우저 푸시 구독 정보 등록 (`endpoint, keys`) | 참가자 |
| DELETE | `/api/push/subscribe` | 구독 해제 | 참가자 |

발송 전용 엔드포인트는 없음 — 콕/매칭/메시지 이벤트 발생 시 서버가 내부적으로 판단해 자동 발송(기능명세 8번 참고).

## 9. 공통 에러 코드

| 코드 | 상황 |
| --- | --- |
| `UNAUTHORIZED` | 로그인 필요 |
| `FORBIDDEN` | 권한 없음 (예: 남의 매칭/신고 접근 시도) |
| `SUSPENDED` | 정지된 계정 |
| `VALIDATION` | 요청값 오류 |
| `NOT_FOUND` | 대상 없음 |

## 10. 이번 문서 범위 밖

- 정확한 필드 타입·nullable 여부는 ERD 확정 후 갱신
- 레포 분리 후 실제 base URL(프론트→백엔드 호출 주소) 확정 필요
