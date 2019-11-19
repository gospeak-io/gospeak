ALTER TABLE requests
    ADD COLUMN cfp_id CHAR(36) REFERENCES cfps (id);
ALTER TABLE requests
    ADD COLUMN event_id CHAR(36) REFERENCES events (id);
ALTER TABLE requests
    ADD COLUMN payload VARCHAR(8192);
