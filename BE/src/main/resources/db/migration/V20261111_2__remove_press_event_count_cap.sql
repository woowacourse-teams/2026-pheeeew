ALTER TABLE press_event_bests
    DROP CONSTRAINT press_event_bests_score_check,
    ADD CONSTRAINT ck_press_event_bests_score_nonnegative CHECK (score >= 0);

ALTER TABLE press_event_attempts
    DROP CONSTRAINT press_event_attempts_total_check,
    DROP CONSTRAINT press_event_attempts_check,
    ADD CONSTRAINT ck_press_event_attempts_total_nonnegative CHECK (total >= 0),
    ADD CONSTRAINT ck_press_event_attempts_best_score_at_least_total CHECK (best_score >= total);
