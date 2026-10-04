ALTER TABLE emotions
    ADD COLUMN region_code VARCHAR(8),
    ADD COLUMN region_classified_at TIMESTAMPTZ,
    ADD CONSTRAINT fk_emotions_region
        FOREIGN KEY (region_code) REFERENCES regions (code),
    ADD CONSTRAINT ck_emotions_region_classification
        CHECK (region_code IS NULL OR
               (region_code ~ '^[0-9]{8}$' AND region_classified_at IS NOT NULL));

CREATE INDEX idx_emotions_region_code ON emotions (region_code);
