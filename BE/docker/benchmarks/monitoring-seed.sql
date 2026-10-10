-- 반드시 MonitoringReproductionIntegrationTest가 만든 빈 임시 DB에서만 실행한다.
INSERT INTO devices (public_id, request_id, platform, created_at, updated_at)
SELECT md5('monitoring-device-' || n)::uuid, md5('monitoring-device-request-' || n)::uuid,
       CASE WHEN n % 2 = 0 THEN 'ANDROID' ELSE 'IOS' END, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM generate_series(1, 100) n;

WITH emd AS (
    SELECT code, display_point, row_number() OVER (ORDER BY code) AS ordinal,
           count(*) OVER () AS size
    FROM regions WHERE level = 'EMD' AND left(code, 2) = '11'
), device AS (
    SELECT id, row_number() OVER (ORDER BY id) AS ordinal FROM devices
)
INSERT INTO emotions (request_id, location, memo, nickname, state, device_id,
                      region_code, region_classified_at, created_at, updated_at)
SELECT md5('monitoring-emotion-' || n)::uuid, e.display_point, '합성 모니터링 데이터', '익명',
       (ARRAY['FRUSTRATED', 'IRRITATED', 'EXHAUSTED', 'DISCOURAGED', 'ANGRY'])[1 + (n - 1) % 5],
       d.id, e.code, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP - INTERVAL '1 minute', CURRENT_TIMESTAMP
FROM generate_series(1, 10000) n
JOIN emd e ON e.ordinal = 1 + (n - 1) % e.size
JOIN device d ON d.ordinal = 1 + (n - 1) % 100;

INSERT INTO device_daily_presses (press_date, state, device_id, press_count, created_at, updated_at)
SELECT (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date, s.state, d.id, 1,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM devices d CROSS JOIN unnest(ARRAY['FRUSTRATED', 'IRRITATED', 'EXHAUSTED', 'DISCOURAGED', 'ANGRY']) s(state);

INSERT INTO app_version (platform, min_supported_version, latest_version, store_url, is_active)
VALUES ('ANDROID', '1.0.0', '1.0.0', 'https://example.invalid/android', TRUE),
       ('IOS', '1.0.0', '1.0.0', 'https://example.invalid/ios', TRUE);

ANALYZE regions;
ANALYZE emotions;
ANALYZE devices;
ANALYZE device_daily_presses;
