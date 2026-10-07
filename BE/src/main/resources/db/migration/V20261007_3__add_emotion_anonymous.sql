SET LOCAL lock_timeout = '5s';

ALTER TABLE emotions
    -- 기존 감정과 익명 여부를 생략하는 구버전 서버의 등록은 익명으로 유지합니다.
    ADD COLUMN anonymous BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT ck_emotions_named_device CHECK (anonymous OR device_id IS NOT NULL);
