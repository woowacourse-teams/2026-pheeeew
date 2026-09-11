ALTER TABLE sigh_reports
    DROP CONSTRAINT uk_sigh_reports_sigh_reporter;

ALTER TABLE sigh_reports
    DROP COLUMN reporter_device_id;

ALTER TABLE sigh_reports
    ADD COLUMN reporter_device_id BIGINT NOT NULL;

ALTER TABLE sigh_reports
    ADD CONSTRAINT fk_sigh_reports_device
        FOREIGN KEY (reporter_device_id) REFERENCES devices (id);

ALTER TABLE sigh_reports
    ADD CONSTRAINT uk_sigh_reports_sigh_reporter
        UNIQUE (sigh_id, reporter_device_id);
