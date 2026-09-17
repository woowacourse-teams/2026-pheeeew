ALTER TABLE sighs
    ADD COLUMN device_id BIGINT;

ALTER TABLE sighs
    ADD CONSTRAINT fk_sighs_device
        FOREIGN KEY (device_id) REFERENCES devices (id);
