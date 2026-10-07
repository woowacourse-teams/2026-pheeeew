-- Must run with all generated staging SQL in one transaction. Never a Flyway migration.
DO $$
DECLARE dataset RECORD;
BEGIN
    SELECT * INTO dataset FROM public.region_datasets
    WHERE dataset_key = 'SGIS_2025_2Q' FOR UPDATE;
    IF NOT FOUND OR dataset.boundaries_verified_at IS NOT NULL THEN
        RAISE EXCEPTION '데이터셋이 없거나 이미 검증되었습니다.';
    END IF;
    IF EXISTS (SELECT 1 FROM public.regions) THEN
        RAISE EXCEPTION '이미 적재한 경계는 덮어쓰지 않습니다.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM sgis_import.sido)
       OR NOT EXISTS (SELECT 1 FROM sgis_import.sigungu)
       OR NOT EXISTS (SELECT 1 FROM sgis_import.dong) THEN
        RAISE EXCEPTION '세 단계의 원본 경계가 모두 필요합니다.';
    END IF;
END $$;

INSERT INTO public.regions (code, level, name, parent_code, boundary, display_point)
SELECT sido_cd, 'SIDO', sido_nm, NULL, boundary, ST_PointOnSurface(boundary)
FROM (
    SELECT sido_cd, sido_nm, ST_Multi(ST_Transform(geom, 4326)) AS boundary
    FROM sgis_import.sido
) source;

INSERT INTO public.regions (code, level, name, parent_code, boundary, display_point)
SELECT sigungu_cd, 'SIGUNGU', sigungu_nm, left(sigungu_cd, 2), boundary, ST_PointOnSurface(boundary)
FROM (
    SELECT sigungu_cd, sigungu_nm, ST_Multi(ST_Transform(geom, 4326)) AS boundary
    FROM sgis_import.sigungu
) source;

INSERT INTO public.regions (code, level, name, parent_code, boundary, display_point)
SELECT adm_cd, 'EMD', adm_nm, left(adm_cd, 5), boundary, ST_PointOnSurface(boundary)
FROM (
    SELECT adm_cd, adm_nm, ST_Multi(ST_Transform(geom, 4326)) AS boundary
    FROM sgis_import.dong
) source;

-- This schema was created by this import transaction; existing schemas are never removed.
DROP SCHEMA sgis_import CASCADE;
-- Leave verification timestamps NULL until the separate quality checks succeed.
