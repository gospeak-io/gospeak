ALTER TABLE users
    ADD COLUMN title VARCHAR(1024);

ALTER TABLE external_events
    ADD COLUMN kind VARCHAR(12) NOT NULL default 'Conference';
ALTER TABLE external_events
    ALTER COLUMN url DROP NOT NULL;
