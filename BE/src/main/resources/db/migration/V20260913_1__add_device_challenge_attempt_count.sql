-- 복호화가 실패해도 challenge 가 남아 구글 호출을 무한히 낼 수 있었다.
-- 구글을 부르기 전에 이 값을 조건부로 증가시켜 challenge 1건당 호출 수를 묶는다.
ALTER TABLE device_challenges
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
