-- 정산 상태를 사용하지 않는 새 서버와 기존 컬럼을 사용하는 구버전 서버의 공존을 허용한다.
-- 구버전 및 롤백 대상의 의존성이 제거된 뒤 별도 마이그레이션에서 컬럼을 삭제한다.
ALTER TABLE collection_cards
    ALTER COLUMN record_status SET DEFAULT 'OPEN';
