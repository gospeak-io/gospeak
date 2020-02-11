ALTER TABLE events
    ADD COLUMN kind VARCHAR(12) NOT NULL default 'Meetup';

DROP INDEX external_events_name_key;
