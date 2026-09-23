ALTER TABLE sighs RENAME TO emotions;
ALTER TABLE sigh_likes RENAME TO emotion_likes;
ALTER TABLE sigh_reports RENAME TO emotion_reports;
ALTER TABLE sigh_blocks RENAME TO emotion_blocks;

ALTER TABLE emotion_likes RENAME COLUMN sigh_id TO emotion_id;
ALTER TABLE emotion_reports RENAME COLUMN sigh_id TO emotion_id;
ALTER TABLE emotion_blocks RENAME COLUMN sigh_id TO emotion_id;
ALTER TABLE device_blocks RENAME COLUMN origin_sigh_id TO origin_emotion_id;

ALTER TABLE emotions RENAME CONSTRAINT sighs_pkey TO emotions_pkey;
ALTER TABLE emotions RENAME CONSTRAINT uk_sighs_request_id TO uk_emotions_request_id;
ALTER TABLE emotions RENAME CONSTRAINT fk_sighs_device TO fk_emotions_device;

ALTER TABLE emotion_likes RENAME CONSTRAINT sigh_likes_pkey TO emotion_likes_pkey;
ALTER TABLE emotion_likes RENAME CONSTRAINT fk_sigh_likes_sigh TO fk_emotion_likes_emotion;
ALTER TABLE emotion_likes RENAME CONSTRAINT fk_sigh_likes_device TO fk_emotion_likes_device;
ALTER TABLE emotion_likes RENAME CONSTRAINT uk_sigh_likes_sigh_device TO uk_emotion_likes_emotion_device;

ALTER TABLE emotion_reports RENAME CONSTRAINT sigh_reports_pkey TO emotion_reports_pkey;
ALTER TABLE emotion_reports RENAME CONSTRAINT fk_sigh_reports_sigh TO fk_emotion_reports_emotion;
ALTER TABLE emotion_reports RENAME CONSTRAINT fk_sigh_reports_device TO fk_emotion_reports_device;
ALTER TABLE emotion_reports RENAME CONSTRAINT uk_sigh_reports_sigh_reporter TO uk_emotion_reports_emotion_reporter;

ALTER TABLE emotion_blocks RENAME CONSTRAINT sigh_blocks_pkey TO emotion_blocks_pkey;
ALTER TABLE emotion_blocks RENAME CONSTRAINT fk_sigh_blocks_device TO fk_emotion_blocks_device;
ALTER TABLE emotion_blocks RENAME CONSTRAINT fk_sigh_blocks_sigh TO fk_emotion_blocks_emotion;
ALTER TABLE emotion_blocks RENAME CONSTRAINT uk_sigh_blocks_blocker_sigh TO uk_emotion_blocks_blocker_emotion;
ALTER TABLE device_blocks RENAME CONSTRAINT fk_device_blocks_origin_sigh TO fk_device_blocks_origin_emotion;

ALTER INDEX idx_sighs_location_gist RENAME TO idx_emotions_location_gist;

ALTER SEQUENCE sighs_id_seq RENAME TO emotions_id_seq;
ALTER SEQUENCE sigh_likes_id_seq RENAME TO emotion_likes_id_seq;
ALTER SEQUENCE sigh_reports_id_seq RENAME TO emotion_reports_id_seq;
ALTER SEQUENCE sigh_blocks_id_seq RENAME TO emotion_blocks_id_seq;
