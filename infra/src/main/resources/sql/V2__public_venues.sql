ALTER TABLE groups
    ADD COLUMN location_id VARCHAR(150);
ALTER TABLE external_cfps
    ADD COLUMN location_id VARCHAR(150);
ALTER TABLE venues
    ADD COLUMN address_id VARCHAR(150) NOT NULL DEFAULT '';
ALTER TABLE venues
    ADD COLUMN address_locality VARCHAR(150);

ALTER TABLE contacts
    RENAME COLUMN description TO notes;
ALTER TABLE venues
    RENAME COLUMN description TO notes;
