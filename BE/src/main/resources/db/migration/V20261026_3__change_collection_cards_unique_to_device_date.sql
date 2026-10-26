ALTER TABLE collection_cards
    DROP CONSTRAINT uk_collection_cards_device_date_state,
    ADD CONSTRAINT uk_collection_cards_device_date UNIQUE (device_id, card_date);
