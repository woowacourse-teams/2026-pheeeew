-- 날짜별 전체 집계와 날짜·기기별 개인 조회를 함께 지원한다.
ALTER TABLE device_daily_presses
    DROP CONSTRAINT uk_device_daily_presses_date_state_device,
    ADD CONSTRAINT uk_device_daily_presses_date_state_device
        UNIQUE (press_date, device_id, state);
