-- 수동 실행 전용. 배포나 Flyway에서 자동 실행하지 않습니다.
-- psql -X --single-transaction -v ON_ERROR_STOP=1 -v batch_size=1000 -v upper_id=... -f 이_파일
-- 한 호출은 한 배치입니다. 처리 0건은 잠긴 행이나 ID 상한 밖의 미분류가 없다는 뜻이 아닙니다.
-- 처리 건수는 psql 종료 코드가 0일 때만 커밋된 결과로 해석합니다.
CREATE TEMP TABLE emotion_region_backfill_input
(
    batch_size INTEGER NOT NULL CHECK (batch_size > 0),
    upper_id BIGINT NOT NULL CHECK (upper_id >= 0)
) ON COMMIT DROP;
INSERT INTO emotion_region_backfill_input VALUES (:'batch_size'::INTEGER, :'upper_id'::BIGINT);

DO $$
BEGIN
    -- (596, 1)은 같은 DB의 감정 행정동 백필 배치·완료 검증에 예약한 잠금 키입니다.
    IF NOT pg_try_advisory_xact_lock(596, 1) THEN
        RAISE EXCEPTION '다른 감정 행정동 백필 배치가 실행 중입니다.';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.region_datasets
        WHERE dataset_key = 'SGIS_2025_2Q' AND boundaries_verified_at IS NOT NULL
    ) THEN
        RAISE EXCEPTION '검증된 SGIS_2025_2Q 경계가 필요합니다.';
    END IF;
END $$;

WITH candidates AS MATERIALIZED (
    SELECT id, location FROM public.emotions
    WHERE region_classified_at IS NULL
      AND id <= (SELECT upper_id FROM emotion_region_backfill_input)
    ORDER BY id
    LIMIT (SELECT batch_size FROM emotion_region_backfill_input)
    FOR NO KEY UPDATE SKIP LOCKED
), updated AS (
    UPDATE public.emotions e
    SET region_code = (
        SELECT r.code FROM public.regions r
        WHERE r.level = 'EMD' AND ST_Covers(r.boundary, c.location)
        ORDER BY r.code LIMIT 1
    ), region_classified_at = CURRENT_TIMESTAMP
    FROM candidates c
    WHERE e.id = c.id AND e.region_classified_at IS NULL
    RETURNING e.id
)
SELECT count(*) AS processed_count FROM updated;
