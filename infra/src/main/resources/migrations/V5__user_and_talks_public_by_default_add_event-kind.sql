UPDATE users
SET status = 'Public'
WHERE status = 'Undefined';

UPDATE talks
SET status = 'Public'
WHERE status = 'Draft'
   OR status = 'Listed';

ALTER TABLE events
    ADD COLUMN kind VARCHAR(12) NOT NULL default 'Meetup';
