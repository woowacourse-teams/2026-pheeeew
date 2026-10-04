-- 수동 실행 전용. 전환 시작 SQL을 먼저 실행하고 새 분류 함수가 배포된 뒤 사용합니다.
-- psql -X --single-transaction -v ON_ERROR_STOP=1 -v batch_size=1000 -v upper_id=... -qAt -f 이_파일
-- 처리 0건은 잠긴 행이나 ID 상한 밖의 대상이 없다는 뜻이 아닙니다. 별도 완료 검증이 필요합니다.
-- 종료 코드 0일 때만 커밋된 건수입니다. 배치 재실행은 집계 완료 표시를 해제하지 않습니다.
CREATE TEMP TABLE emotion_region_reclassification_input
(
    batch_size INTEGER NOT NULL CHECK (batch_size > 0),
    upper_id BIGINT NOT NULL CHECK (upper_id >= 0)
) ON COMMIT DROP;
INSERT INTO emotion_region_reclassification_input VALUES (:'batch_size'::INTEGER, :'upper_id'::BIGINT);

DO $$
BEGIN
    IF NOT pg_try_advisory_xact_lock(596, 1) THEN
        RAISE EXCEPTION '다른 감정 행정동 백필 배치가 실행 중입니다.';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.region_datasets
        WHERE dataset_key = 'SGIS_2025_2Q' AND boundaries_verified_at IS NOT NULL
    ) THEN
        RAISE EXCEPTION '검증된 SGIS_2025_2Q 경계가 필요합니다.';
    END IF;
    IF EXISTS (SELECT 1 FROM public.region_datasets WHERE dataset_key = 'SGIS_2025_2Q' AND backfill_verified_at IS NOT NULL)
       AND EXISTS (
           SELECT 1 FROM public.emotions
           WHERE region_code IS NULL AND id <= (SELECT upper_id FROM emotion_region_reclassification_input)
             AND public.find_emd_region_code(location) IS NOT NULL
       ) THEN
        RAISE EXCEPTION '재분류 시작 SQL로 집계 완료 표시를 먼저 해제해야 합니다.';
    END IF;
END $$;

WITH candidates AS MATERIALIZED (
    SELECT id, public.find_emd_region_code(location) AS region_code FROM public.emotions
    WHERE region_code IS NULL
      AND id <= (SELECT upper_id FROM emotion_region_reclassification_input)
      AND public.find_emd_region_code(location) IS NOT NULL
    ORDER BY id
    LIMIT (SELECT batch_size FROM emotion_region_reclassification_input)
    FOR NO KEY UPDATE SKIP LOCKED
), updated AS (
    UPDATE public.emotions e
    SET region_code = c.region_code, region_classified_at = CURRENT_TIMESTAMP
    FROM candidates c
    WHERE e.id = c.id AND e.region_code IS NULL AND c.region_code IS NOT NULL
      AND EXISTS (SELECT 1 FROM public.region_datasets WHERE dataset_key = 'SGIS_2025_2Q' AND backfill_verified_at IS NULL)
    RETURNING e.id
)
SELECT count(*) AS processed_count FROM updated;
