CREATE TABLE proposal_ratings
(
    proposal_id CHAR(36)  NOT NULL REFERENCES proposals (id),
    grade       INT       NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    created_by  CHAR(36)  NOT NULL REFERENCES users (id),
    UNIQUE (proposal_id, created_by)
);

ALTER TABLE groups
    ADD COLUMN logo VARCHAR(1024);
ALTER TABLE groups
    ADD COLUMN banner VARCHAR(1024);
ALTER TABLE groups
    ADD COLUMN website VARCHAR(1024);
ALTER TABLE groups
    ADD COLUMN social_facebook VARCHAR(1024);
ALTER TABLE groups
    ADD COLUMN social_instagram VARCHAR(1024);
ALTER TABLE groups
    ADD COLUMN social_twitter VARCHAR(1024);
ALTER TABLE groups
    ADD COLUMN social_linkedIn VARCHAR(1024);
ALTER TABLE groups
    ADD COLUMN social_youtube VARCHAR(1024);
ALTER TABLE groups
    ADD COLUMN social_meetup VARCHAR(1024);
ALTER TABLE groups
    ADD COLUMN social_eventbrite VARCHAR(1024);
ALTER TABLE groups
    ADD COLUMN social_slack VARCHAR(1024);
ALTER TABLE groups
    ADD COLUMN social_discord VARCHAR(1024);
ALTER TABLE groups
    ADD COLUMN status VARCHAR(10) NOT NULL default 'Active';
