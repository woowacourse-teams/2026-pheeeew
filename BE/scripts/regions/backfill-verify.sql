-- 수동 실행 전용. 모든 등록을 새 서버로 전환하고 구버전의 진행 중 등록을 끝낸 뒤 실행합니다.
-- psql -X --single-transaction -v ON_ERROR_STOP=1 -qAt -f 이_파일
-- 종료 코드 0일 때만 완료입니다. 완료 후 구버전·직접 SQL의 미분류 재유입을 차단하지 않습니다.
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SET LOCAL statement_timeout = '5s';

DO $$
BEGIN
    IF NOT pg_try_advisory_xact_lock(596, 1) THEN
        RAISE EXCEPTION '다른 감정 행정동 백필 배치가 실행 중입니다.';
    END IF;
END $$;

LOCK TABLE public.emotions IN SHARE MODE NOWAIT;

-- 잠금 획득 뒤 별도 명령에서 검사하여 먼저 커밋된 등록도 포함합니다.
DO $$
DECLARE verified_at TIMESTAMPTZ;
BEGIN
    SELECT boundaries_verified_at INTO verified_at FROM public.region_datasets
    WHERE dataset_key = 'SGIS_2025_2Q' FOR UPDATE;
    IF NOT FOUND OR verified_at IS NULL THEN
        RAISE EXCEPTION '검증된 SGIS_2025_2Q 경계가 필요합니다.';
    END IF;
    IF EXISTS (SELECT 1 FROM public.emotions WHERE region_classified_at IS NULL) THEN
        RAISE EXCEPTION '미분류 감정이 남아 있습니다.';
    END IF;
    UPDATE public.region_datasets
    SET backfill_verified_at = COALESCE(backfill_verified_at, clock_timestamp())
    WHERE dataset_key = 'SGIS_2025_2Q';
END $$;

SELECT backfill_verified_at FROM public.region_datasets WHERE dataset_key = 'SGIS_2025_2Q';
