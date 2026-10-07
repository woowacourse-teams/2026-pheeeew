-- 경계 적재·백필·승인된 잔여 기록 정리 후 적용합니다. NULL이 남으면 적용을 실패시킵니다.
SET LOCAL lock_timeout = '5s';

ALTER TABLE public.emotions
    ALTER COLUMN region_code SET NOT NULL,
    ALTER COLUMN region_classified_at SET NOT NULL;
