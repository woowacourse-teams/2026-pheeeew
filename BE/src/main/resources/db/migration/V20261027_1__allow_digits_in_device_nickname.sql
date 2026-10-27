SET LOCAL lock_timeout = '5s';

ALTER TABLE devices
    DROP CONSTRAINT ck_devices_nickname,
    ADD CONSTRAINT ck_devices_nickname CHECK (
        nickname = btrim(nickname)
        AND nickname <> '익명'
        AND nickname ~ '^[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9 ]{1,10}$'
    );
