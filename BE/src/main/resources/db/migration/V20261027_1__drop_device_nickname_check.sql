SET LOCAL lock_timeout = '5s';

ALTER TABLE devices
    DROP CONSTRAINT ck_devices_nickname;
