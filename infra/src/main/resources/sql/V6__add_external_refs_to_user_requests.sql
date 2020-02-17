ALTER TABLE user_requests
    ADD COLUMN external_event_id CHAR(36) REFERENCES external_events (id);
ALTER TABLE user_requests
    ADD COLUMN external_cfp_id CHAR(36) REFERENCES external_cfps (id);
ALTER TABLE user_requests
    ADD COLUMN external_proposal_id CHAR(36) REFERENCES external_proposals (id);
