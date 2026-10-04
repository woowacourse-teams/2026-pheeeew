CREATE TABLE region_datasets
(
    dataset_key            VARCHAR(20) PRIMARY KEY,
    boundaries_verified_at TIMESTAMPTZ,
    backfill_verified_at   TIMESTAMPTZ,
    CONSTRAINT ck_region_datasets_key
        CHECK (dataset_key = 'SGIS_2025_2Q'),
    CONSTRAINT ck_region_datasets_verification
        CHECK (backfill_verified_at IS NULL OR
               (boundaries_verified_at IS NOT NULL AND backfill_verified_at >= boundaries_verified_at))
);

INSERT INTO region_datasets (dataset_key) VALUES ('SGIS_2025_2Q');

CREATE TABLE regions
(
    code          VARCHAR(8) PRIMARY KEY,
    level         VARCHAR(7) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    parent_code   VARCHAR(8),
    boundary      geometry(MultiPolygon, 4326) NOT NULL,
    display_point geometry(Point, 4326) NOT NULL,
    CONSTRAINT ck_regions_code_level
        CHECK ((level = 'SIDO' AND code ~ '^[0-9]{2}$') OR
               (level = 'SIGUNGU' AND code ~ '^[0-9]{5}$') OR
               (level = 'EMD' AND code ~ '^[0-9]{8}$')),
    CONSTRAINT ck_regions_name CHECK (btrim(name) <> ''),
    CONSTRAINT fk_regions_parent
        FOREIGN KEY (parent_code) REFERENCES regions (code),
    CONSTRAINT ck_regions_parent
        CHECK ((level = 'SIDO' AND parent_code IS NULL) OR
               (level = 'SIGUNGU' AND parent_code IS NOT NULL AND parent_code = left(code, 2)) OR
               (level = 'EMD' AND parent_code IS NOT NULL AND parent_code = left(code, 5))),
    CONSTRAINT ck_regions_boundary
        CHECK (NOT ST_IsEmpty(boundary) AND ST_IsValid(boundary) AND
               ST_XMin(boundary) >= -180 AND ST_XMax(boundary) <= 180 AND
               ST_YMin(boundary) >= -90 AND ST_YMax(boundary) <= 90),
    CONSTRAINT ck_regions_display_point
        CHECK (NOT ST_IsEmpty(display_point) AND ST_Covers(boundary, display_point))
);

CREATE INDEX idx_regions_boundary_gist ON regions USING GIST (boundary);
CREATE INDEX idx_regions_parent ON regions (parent_code);
