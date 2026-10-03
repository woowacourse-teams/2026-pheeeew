-- Run with psql -X --single-transaction -v ON_ERROR_STOP=1 and all three expected counts.
-- Quoted psql variables are expanded outside the DO body, then checked as positive integers.
CREATE TEMP TABLE sgis_expected_counts
(
    level TEXT PRIMARY KEY,
    expected_count INTEGER NOT NULL CHECK (expected_count > 0)
) ON COMMIT DROP;
INSERT INTO sgis_expected_counts VALUES
    ('SIDO', :'sido_count'::INTEGER),
    ('SIGUNGU', :'sigungu_count'::INTEGER),
    ('EMD', :'emd_count'::INTEGER);

-- Approved SGIS_2025_2Q exceptions only. Areas use the same EPSG:5179 formula as the source audit.
CREATE TEMP TABLE sgis_allowed_overlaps
(
    first_code TEXT,
    second_code TEXT,
    max_area_m2 DOUBLE PRECISION NOT NULL CHECK (max_area_m2 > 0),
    PRIMARY KEY (first_code, second_code),
    CHECK (first_code < second_code)
) ON COMMIT DROP;
INSERT INTO sgis_allowed_overlaps VALUES
    ('23090590', '23090600', 1),
    ('23090600', '23090610', 1),
    ('23040600', '23090600', 1),
    ('23090660', '23090760', 1),
    ('23090670', '23090760', 1),
    ('23090570', '23090760', 1),
    ('23090540', '23090760', 1),
    ('23090600', '23090740', 1),
    ('23040530', '23090600', 1),
    ('23090610', '23090760', 1),
    ('23040560', '23090600', 1),
    ('23010540', '23090600', 1),
    ('23090590', '23090760', 1),
    ('23090560', '23090760', 1);

DO $$
DECLARE
    verified_at TIMESTAMPTZ;
    overlapping RECORD;
BEGIN
    -- import.sql takes the same lock and rejects changes after verification.
    SELECT boundaries_verified_at INTO verified_at FROM public.region_datasets
    WHERE dataset_key = 'SGIS_2025_2Q' FOR UPDATE;
    IF NOT FOUND OR verified_at IS NOT NULL THEN
        RAISE EXCEPTION '데이터셋이 없거나 이미 검증되었습니다.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM sgis_expected_counts e
        WHERE e.expected_count <> (SELECT count(*) FROM public.regions r WHERE r.level = e.level)
    ) THEN
        RAISE EXCEPTION '원본과 적재된 레벨별 경계 개수가 다릅니다.';
    END IF;

    -- Containment and identical polygons are rejected even for an approved pair below the area cap.
    -- Boundary-only contact is allowed. The explicit bbox condition enables GiST candidate selection.
    SELECT a.code AS first_code, b.code AS second_code INTO overlapping
    FROM public.regions a JOIN public.regions b
      ON a.level = b.level AND a.code < b.code AND a.boundary && b.boundary
    LEFT JOIN sgis_allowed_overlaps allowed ON a.code = allowed.first_code AND b.code = allowed.second_code
    WHERE ST_Relate(a.boundary, b.boundary, '2********') AND (
        a.level <> 'EMD' OR allowed.first_code IS NULL
        OR ST_Covers(a.boundary, b.boundary) OR ST_Covers(b.boundary, a.boundary)
        OR ST_Area(ST_Transform(ST_Intersection(a.boundary, b.boundary), 5179)) > allowed.max_area_m2
    ) LIMIT 1;
    IF FOUND THEN
        RAISE EXCEPTION '같은 레벨의 경계 면적이 겹칩니다: %, %', overlapping.first_code, overlapping.second_code;
    END IF;

    UPDATE public.region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP
    WHERE dataset_key = 'SGIS_2025_2Q';
END $$;
