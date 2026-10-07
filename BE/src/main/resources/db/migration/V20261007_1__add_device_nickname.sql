SET LOCAL lock_timeout = '5s';

ALTER TABLE devices
    -- 한국어 닉네임 백필 전에도 구버전 서버의 기기 등록을 허용합니다.
    ADD COLUMN nickname VARCHAR(10) COLLATE "C",
    ADD CONSTRAINT ck_devices_nickname CHECK (
        nickname = btrim(nickname)
        AND nickname <> '익명'
        AND nickname ~ '^[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z ]{1,10}$'
    );

CREATE UNIQUE INDEX uk_devices_nickname ON devices (lower(nickname));
