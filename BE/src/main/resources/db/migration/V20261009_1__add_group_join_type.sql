SET LOCAL lock_timeout = '5s';

ALTER TABLE groups
    -- 기존 그룹과, 가입 방식을 보내지 않는 구버전 서버의 생성은 비공개로 유지합니다.
    -- 이 DEFAULT 는 전환 기간용이 아니라 영구입니다. 롤링 배포와 롤백에서 구버전 서버가
    -- join_type 없이 INSERT 하므로, 지우면 그 INSERT 가 NOT NULL 위반으로 실패합니다.
    ADD COLUMN join_type VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    ADD CONSTRAINT ck_groups_join_type CHECK (join_type IN ('PUBLIC', 'PRIVATE'));
