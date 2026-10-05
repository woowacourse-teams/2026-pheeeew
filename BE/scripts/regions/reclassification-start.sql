-- 수동 전환 시작 전용. Flyway나 배치마다 실행하지 않습니다.
-- psql -X --single-transaction -v ON_ERROR_STOP=1 -qAt -f 이_파일
-- 완료 후 배치 재시도에는 실행하지 않습니다. 다시 실행하면 집계 완료 표시가 해제됩니다.
-- 커밋 뒤 새 준비 검사부터 지역 집계는 503입니다. 이미 검사를 통과한 요청은 완료될 수 있습니다.
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SET LOCAL statement_timeout = '5s';

DO $$
BEGIN
    IF NOT pg_try_advisory_xact_lock(596, 1) THEN
        RAISE EXCEPTION '다른 감정 행정동 백필 배치가 실행 중입니다.';
    END IF;
    PERFORM 1 FROM public.region_datasets
    WHERE dataset_key = 'SGIS_2025_2Q' AND boundaries_verified_at IS NOT NULL
    FOR UPDATE NOWAIT;
    IF NOT FOUND THEN
        RAISE EXCEPTION '검증된 SGIS_2025_2Q 경계가 필요합니다.';
    END IF;
    UPDATE public.region_datasets SET backfill_verified_at = NULL
    WHERE dataset_key = 'SGIS_2025_2Q';
END $$;

-- 종료 코드 0일 때만 전환 시작이 커밋됐습니다. 재분류 완료 검증으로 집계를 다시 엽니다.
SELECT backfill_verified_at IS NULL AS started FROM public.region_datasets WHERE dataset_key = 'SGIS_2025_2Q';
