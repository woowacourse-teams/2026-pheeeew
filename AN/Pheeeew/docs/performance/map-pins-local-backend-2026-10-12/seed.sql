INSERT INTO regions(code,level,name,parent_code,boundary,display_point) VALUES
('41','SIDO','실험 시도',NULL,ST_Multi(ST_MakeEnvelope(127.14,37.43,127.16,37.45,4326)),ST_SetSRID(ST_MakePoint(127.147538,37.440923),4326)),
('41135','SIGUNGU','실험 시군구','41',ST_Multi(ST_MakeEnvelope(127.14,37.43,127.16,37.45,4326)),ST_SetSRID(ST_MakePoint(127.147538,37.440923),4326)),
('41135109','EMD','실험 읍면동','41135',ST_Multi(ST_MakeEnvelope(127.14,37.43,127.16,37.45,4326)),ST_SetSRID(ST_MakePoint(127.147538,37.440923),4326)) ON CONFLICT DO NOTHING;
DELETE FROM emotions;
INSERT INTO emotions(id,request_id,location,memo,created_at,updated_at,state,rotation_degrees,region_code,region_classified_at)
SELECT 1000000+i, gen_random_uuid(), ST_SetSRID(ST_MakePoint(127.147538+((i%40)/39.0-0.5)*0.003,37.440923+((i/40)/39.0-0.5)*0.003),4326), NULL,
 date_trunc('day',now()),date_trunc('day',now()), (ARRAY['FRUSTRATED','IRRITATED','EXHAUSTED','DISCOURAGED','ANGRY'])[i%5+1], (i%360)::float, '41135109',now()
FROM generate_series(0, :pin_count - 1) i;
ANALYZE emotions;
SELECT count(*) as seeded_pins FROM emotions;
