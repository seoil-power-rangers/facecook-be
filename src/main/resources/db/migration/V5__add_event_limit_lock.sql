-- 행사 전체 하루 콕 한도(CookService.EVENT_WIDE_DAILY_LIMITS)를 동시 요청에서도 지키기 위한 잠금용 행.
-- 이 테이블에는 데이터가 없다. 한도가 있는 날의 콕 전송이 이 행을 FOR UPDATE로 잠가서, 서로 다른 사용자
-- 쌍의 전송도 한 번에 하나씩만 "전체 건수 확인 → 저장"을 하게 만든다. 한도 값이나 건수는 저장하지 않으므로
-- 테스트 데이터 초기화나 구 서버와의 공존에서 실제 콕 건수와 어긋날 일이 없다.
CREATE TABLE event_limit_lock (
    lock_id INT PRIMARY KEY
);

INSERT INTO event_limit_lock (lock_id) VALUES (1);
