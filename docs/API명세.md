# face 콕 — API 명세

정리 기준일: 2026-09-14

- 인증: 세션 쿠키(HttpOnly) 기반. `[참가자]` = 로그인한 참가자 세션 필요, `[관리자]` = 관리자 세션 필요, `[공개]` = 인증 불필요
- 응답 포맷: JSON, 실패 시 `{ "code": "ERROR_CODE", "message": "..." }`
- 정확한 필드 타입은 ERD 확정 후 갱신

## 1. 로그인 / 회원가입

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/auth/request-code` | 이메일 인증코드 발급 (`purpose: signup\|login`) | 공개 |
| POST | `/api/auth/verify-signup` | 인증코드 확인 + 비밀번호 설정 + 회원가입 (`email, code, password, agreedTerms[]`) | 공개 |
| POST | `/api/auth/login` | 참가자 이메일·비밀번호 로그인 | 공개 |
| POST | `/api/auth/verify-login` | 인증코드 확인 + 로그인 (기존 OTP 호환 경로) | 공개 |
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
  "password": "password123",
  "agreedTerms": ["service", "privacy"]
}
```

- `password`: 8자 이상. 서버에는 BCrypt 해시만 저장한다.

`agreedTerms` 값:

| ID | 필수 여부 | 설명 |
| --- | --- | --- |
| `service` | 필수 | 서비스 이용약관 |
| `privacy` | 필수 | 개인정보 수집·이용 동의 |
| `photo` | 선택 | 프로필 사진 업로드 동의 |

### 비밀번호 로그인 요청/응답

`POST /api/auth/login`

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

- 이메일이 없거나 비밀번호가 틀리거나 기존 계정에 비밀번호가 설정되지 않은 경우 모두
  `INVALID_CREDENTIALS`를 반환한다.
- 정지 계정은 `SUSPENDED`를 반환한다.

### 기존 OTP 로그인 요청/응답

`POST /api/auth/verify-login`

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

`verify-signup`, `login`, `verify-login` 성공 응답:

```json
{
  "userId": 1,
  "email": "user@example.com",
  "role": "participant"
}
```

성공 응답에는 기존과 동일한 HttpOnly 세션 쿠키가 함께 발급된다.

## 2. 프로필

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/profile/photo/upload-url` | S3 업로드용 presigned URL 발급 (`contentType`) → `{ uploadUrl, photoUrl }` | 참가자 |
| GET | `/api/profile` | 내 프로필 조회 | 참가자 |
| POST | `/api/profile` | 필수+선택 프로필 최초 등록 (`nickname, gender, age, mbti, hobby, bloodType, department?, grade?, bio?, idealType?, photo?`) | 참가자 |
| PATCH | `/api/profile` | 선택 항목만 수정 (`department?, grade?, bio?, photo?`) — 필수 필드는 요청 자체에 안 받음 | 참가자 |
| GET | `/api/profiles` | 참가자 목록 (본인 제외) | 참가자 |
| GET | `/api/profiles/filters?active=` | 실제 참가자가 가진 학과·MBTI·취미 값(정렬됨). `active=true`면 활동 중인 참가자만 대상 | 참가자 |
| GET | `/api/profiles/{userId}` | 특정 참가자 프로필 상세 | 참가자 |
| GET | `/api/stats` | 참가자용 `{ total, activeNow }` — 등록된 프로필 수, 최근 15분 내 활동한 참가자 수 | 참가자 |
| GET | `/api/departments` | 학과 정본을 학부별로 묶어서 반환 (`[{ college, majors[] }]`) | 참가자 |

### 프로필 응답의 활동 정보

`GET /api/profile`, `GET /api/profiles`, `GET /api/profiles/{userId}`의 각 항목에
`lastActiveAt`(ISO 8601 문자열, 활동 기록 없으면 `null`)과 `isActive`(boolean)가
포함된다. "활동 중" 기준은 서버가 관리하는 최근 15분 롤링 윈도우다
(`app.profile.activity.active-window-minutes`, 기본 15) — 클라이언트가 별도로
계산하지 않는다.

`users.last_active_at`은 인증된 요청마다 갱신되지만, 같은 사용자에 대해
30초 안에 이미 갱신됐으면 다시 쓰지 않는다(디바운스) — 폴링이 잦은 화면이
많아 매 요청마다 쓰기를 발생시키지 않기 위함이다.

### 학과(`department`) 값 검증

`POST /api/profile`, `PATCH /api/profile`의 `department`는 `GET /api/departments`가
반환하는 학부 7개·학과 30개 목록 중 하나만 허용한다. 목록에 없는 값이면
`VALIDATION` 400을 반환한다. 자유 입력이던 시절 가입자에게 남은 값은 그대로
유지되며(기존 데이터는 소급 검증하지 않음), 새로 저장되는 값만 검증 대상이다.

facecook-fe는 이 목록을 하드코딩하지 않고 `GET /api/departments`로 받아
`DepartmentPicker`를 그린다 — BE의 `DepartmentCatalog`가 유일한 정본이다.

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

응답:

```json
{
  "totalUsers": 214,
  "activeToday": 200,
  "totalCooks": 487,
  "totalMatches": 63,
  "missionCleared": 21,
  "pendingReports": 2
}
```

- `activeToday`는 기본적으로 `users.status = ACTIVE`인 사용자 수다.
- `ADMIN_STATS_ACTIVE_USER_CRITERION=LAST_ACTIVE_TODAY`로 설정하면
  `Asia/Seoul` 기준 당일 `last_active_at`이 기록된 사용자 수를 집계한다.
  `last_active_at`은 인증된 요청마다 갱신되므로(2절 참고) 이 기준을 실제로
  쓸 수 있다.
- 이 "하루 단위" 기준은 참가자용 `GET /api/stats`의 `activeNow`(15분 롤링
  기준)와 일부러 다르다 — 여긴 운영 리포트용, 그쪽은 실시간에 가까운
  참가자 화면용이라 두 수치가 다르게 나오는 게 정상이다.

## 8. 알림 (웹 푸시)

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/push/vapid-public-key` | 브라우저 구독 생성에 사용할 VAPID 공개키 조회 | 참가자 |
| POST | `/api/push/subscribe` | 브라우저 푸시 구독 정보 등록 (`endpoint, keys`) | 참가자 |
| DELETE | `/api/push/subscribe` | 현재 사용자의 모든 기기 구독 해제 | 참가자 |

`GET /api/push/vapid-public-key` 응답:

```json
{
  "publicKey": "base64url-encoded-vapid-public-key"
}
```

`POST /api/push/subscribe` 요청:

```json
{
  "endpoint": "https://push.example/subscription",
  "keys": {
    "p256dh": "browser-public-key",
    "auth": "browser-auth-secret"
  }
}
```

- 동일한 `(user_id, endpoint)`를 재등록하면 `keys`를 갱신한다.
- 등록과 해제 성공 응답은 모두 `204 No Content`이다.

발송 전용 엔드포인트는 없음 — 콕/매칭/메시지 이벤트 발생 시 서버가 내부적으로 판단해 자동 발송(기능명세 8번 참고).

## 9. 공통 에러 코드

| 코드 | 상황 |
| --- | --- |
| `UNAUTHORIZED` | 로그인 필요 |
| `FORBIDDEN` | 권한 없음 (예: 남의 매칭/신고 접근 시도) |
| `SUSPENDED` | 정지된 계정 |
| `VALIDATION` | 요청값 오류 |
| `NOT_FOUND` | 대상 없음 |
| `INVALID_CREDENTIALS` | 참가자 이메일 또는 비밀번호 불일치(비밀번호 미설정 기존 계정 포함) |

## 10. 이번 문서 범위 밖

- 정확한 필드 타입·nullable 여부는 ERD 확정 후 갱신
- 레포 분리 후 실제 base URL(프론트→백엔드 호출 주소) 확정 필요
