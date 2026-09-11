# chat 도메인 구현 보고서

## 구현 기준

- 기준 브랜치: PR #10이 머지된 `main`의 `97e2266`
- REST: `GET /api/matches/{matchId}/messages`
- STOMP: `/ws`, `/topic/chat/{matchId}`, `/app/chat/{matchId}/send`
- 메시지 영속화: V1의 `message` 테이블과 `client_message_id` UNIQUE 제약 사용
- 서버 간 중계: Redis Pub/Sub 채널 `facecook:chat:messages`

chat 패키지에는 `match_info` 엔티티를 새로 만들지 않았다. REST 조회, 구독,
전송의 매칭 당사자 판정은 모두 cook의 `MatchInfoRepository.findById`와
`MatchInfo.includes(userId)`를 호출하는 `ChatAuthorizationService`를 거친다.

## WebSocket 인증 설계와 판단 근거

브라우저 WebSocket API는 임의의 HTTP 인증 헤더를 붙이기 어렵지만 같은
사이트의 쿠키는 `/ws` 업그레이드 요청에 함께 보낸다. 따라서 최초 인증은
STOMP CONNECT header가 아니라 HTTP handshake에서 수행한다.

1. `ChatHandshakeInterceptor`가 `HttpServletRequest`의
   `FACECOOK_SESSION`(설정값 사용) 쿠키를 읽는다.
2. REST와 동일한 `SessionTokenSigner`로 서명과 만료를 검증한다.
3. 토큰의 userId로 기존 `UserRepository`를 조회해 탈퇴성 불일치와
   `SUSPENDED` 상태를 확인한다.
4. 검증한 `AuthenticatedUser`와 원본 서명 토큰을 WebSocket session
   attribute에 저장한다.
5. `ChatHandshakeHandler`가 이를 `ChatPrincipal`로 바꿔 STOMP Principal에
   연결한다. 이후 컨트롤러는 클라이언트가 보낸 userId가 아니라 이
   Principal의 userId만 사용한다.

이 경로는 REST의 `SessionAuthenticationInterceptor`와 독립적이다.
`WebConfig`는 변경하지 않았고, `/ws`에만 handshake interceptor를 등록했다.
허용 Origin은 REST와 같은 `CorsProperties`를 사용해 쿠키를 이용한 cross-site
WebSocket 연결을 제한한다.

WebSocket은 연결이 오래 유지되므로 handshake 결과를 계속 신뢰하지 않는다.
`ChatInboundChannelInterceptor`는 CONNECT·SUBSCRIBE·모든 SEND에서 session에
보관한 원본 토큰을 `SessionTokenSigner`로 다시 검증하고 `UserRepository`를
재조회한다. 따라서 연결 뒤 토큰이 만료되거나 계정이 정지돼도 다음 SEND가
즉시 `UNAUTHORIZED` 또는 `SUSPENDED`로 거절된다. SUBSCRIBE에서는 목적지의
matchId를 파싱한 뒤 `MatchInfo.includes`로 당사자를 확인한다.

또한 클라이언트 SEND 목적지를 `/app/chat/{matchId}/send`로만 제한한다.
그렇지 않으면 simple broker의 `/topic/**`에 직접 SEND해 DB 저장과 서비스
권한 검사를 우회할 수 있기 때문이다. 허용된 SEND도 인바운드 계층과 서비스
계층에서 당사자 여부를 각각 확인한다.

## 저장, 멱등성, ACK

`clientMessageId`는 UUID로 검증하고 `message.client_message_id`에 저장한다.
먼저 같은 UUID를 조회하고, 이미 존재하면 새 row를 만들지 않고 기존 메시지
응답을 그대로 반환한다. 동시에 같은 UUID가 들어오는 경쟁 상황은 DB UNIQUE
제약이 최종 방어선이다. `saveAndFlush`의 독립 repository 트랜잭션이 duplicate로
롤백된 뒤 기존 row를 다시 조회한다. UUID가 다른 사용자나 matchId에서
재사용된 경우에는 기존 채팅 내용이 노출되지 않도록 `VALIDATION`으로 거절한다.

단순 STOMP receipt는 비동기 message channel에 접수됐다는 의미일 수 있어 DB
커밋 완료 신호로 사용하지 않는다. 컨트롤러가 영속화 결과를 받은 뒤에만
`/user/queue/chat-acks`로 메시지를 반환한다. ACK를 받지 못해 재전송하더라도
같은 `clientMessageId`의 기존 메시지가 돌아온다.

운영시간은 cook의 기준과 동일하게 `Clock`의 instant를
`ZoneId.of("Asia/Seoul")`로 변환해 판정한다. 09:00은 포함하고 18:00부터는
`CLOSED`다. 조회에는 시간 제한을 적용하지 않는다.

## Redis Pub/Sub 범위

메시지 저장 후 `ChatMessagePublisher`가 JSON을 Redis에 publish한다. 각 서버의
`RedisMessageListenerContainer`가 같은 채널을 subscribe하고,
`ChatMessageSubscriber`가 자기 인스턴스의 `/topic/chat/{matchId}`로 전달한다.
연결·종료 이벤트는 사용자별 Redis Set에 sessionId를 추가·삭제해 접속자 명단
구조도 마련했다.

이 구현은 여러 서버 인스턴스가 같은 Redis 채널을 구독하는 중계 구조까지
포함한다. 다만 현재 로컬 1인스턴스 개발 단계에서는 실제 서버 두 대와 ALB를
띄운 end-to-end broker relay 검증은 하지 않았다. 배포 단계에서 두 인스턴스에
서로 다른 사용자를 연결하고, 한쪽의 DB 저장·publish가 다른 쪽 STOMP session에
전달되는지와 Redis 장애 후 히스토리 복구를 확인해야 한다.

## 테스트 범위

- REST는 `@WebMvcTest`로 세션 필수, 기본/명시 pagination 파라미터, 응답 형식,
  잘못된 범위를 검증한다.
- 서비스 단위 테스트로 `before` cursor, UUID 재전송, 동시 duplicate 경쟁,
  UUID 교차 재사용 차단, 서울 시간 09:00/18:00 경계를 검증한다.
- STOMP는 socket-level 통합 테스트가 아니라 경계 컴포넌트 단위 테스트로
  검증했다. handshake 쿠키 인증·정지 차단, SUBSCRIBE 당사자 검증, 매 SEND의
  토큰/정지 재조회, broker topic 직접 SEND 차단, JSON ERROR frame 변환,
  저장 결과 publish/ACK 반환을 각각 테스트한다.
- 실제 Redis listener와 두 서버 간 전달은 위 범위대로 배포 단계 통합 테스트로
  남겼다.

전체 `./gradlew test`를 JDK 21에서 통과시켰다. 또한 Docker Compose의 MySQL 8과
Redis 7을 기동한 상태에서 애플리케이션을 실행해 Flyway V1 적용, Hibernate
`ddl-auto=validate`의 `Message` 매핑 검증, Redis listener container 연결,
STOMP simple broker와 `/ws` endpoint를 포함한 Spring context 기동까지 확인했다.
이 기동 확인은 실제 STOMP client 두 개를 연결한 socket-level 통합 테스트나
서버 2대 중계 테스트를 의미하지는 않는다.
