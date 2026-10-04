CREATE INDEX idx_regions_emd_geography_gist ON regions USING GIST ((boundary::geography))
    WHERE level = 'EMD';

-- 앱 분류와 수동 백필이 같은 규칙을 사용합니다. 경계 검증 상태는 호출자가 확인합니다.
CREATE FUNCTION public.find_emd_region_code(location geometry) RETURNS VARCHAR(8)
    LANGUAGE plpgsql STABLE STRICT
AS $$
DECLARE
    region_code VARCHAR(8);
BEGIN
    SELECT code INTO region_code FROM public.regions
    WHERE level = 'EMD' AND ST_Covers(boundary, location)
    ORDER BY code LIMIT 1;

    IF region_code IS NOT NULL THEN
        RETURN region_code;
    END IF;

    SELECT code INTO region_code FROM public.regions
    WHERE level = 'EMD'
      AND ST_DWithin(boundary::geography, location::geography, 1000, true)
    ORDER BY ST_Distance(boundary::geography, location::geography, true), code
    LIMIT 1;

    RETURN region_code;
END;
$$;
