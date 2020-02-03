CREATE TABLE external_events
(
    id                CHAR(36)      NOT NULL PRIMARY KEY,
    name              VARCHAR(120)  NOT NULL UNIQUE,
    logo              VARCHAR(1024),
    description       VARCHAR(4096) NOT NULL,
    start             TIMESTAMP,
    finish            TIMESTAMP,
    location          VARCHAR(4096),
    location_id       VARCHAR(150),
    location_lat      DOUBLE PRECISION,
    location_lng      DOUBLE PRECISION,
    location_locality VARCHAR(50),
    location_country  VARCHAR(30),
    url               VARCHAR(1024) NOT NULL,
    tickets_url       VARCHAR(1024),
    videos_url        VARCHAR(1024),
    twitter_account   VARCHAR(120),
    twitter_hashtag   VARCHAR(120),
    tags              VARCHAR(150)  NOT NULL, -- 5 tags max
    created_at        TIMESTAMP     NOT NULL,
    created_by        CHAR(36)      NOT NULL REFERENCES users (id),
    updated_at        TIMESTAMP     NOT NULL,
    updated_by        CHAR(36)      NOT NULL REFERENCES users (id)
);
CREATE INDEX external_events_start_idx ON external_events (start);
CREATE INDEX external_events_location_lat_idx ON external_events (location_lat);
CREATE INDEX external_events_location_lng_idx ON external_events (location_lng);

CREATE TABLE external_proposals
(
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    talk_id     CHAR(36)      NOT NULL REFERENCES talks (id),
    event_id    CHAR(36)      NOT NULL REFERENCES external_events (id),
    status      VARCHAR(10)   NOT NULL,
    title       VARCHAR(120)  NOT NULL,
    duration    BIGINT        NOT NULL,
    description VARCHAR(4096) NOT NULL,
    message     VARCHAR(4096) NOT NULL,
    speakers    VARCHAR(184)  NOT NULL, -- 5 speakers max
    slides      VARCHAR(1024),
    video       VARCHAR(1024),
    tags        VARCHAR(150)  NOT NULL, -- 5 tags max
    url         VARCHAR(1024),
    created_at  TIMESTAMP     NOT NULL,
    created_by  CHAR(36)      NOT NULL REFERENCES users (id),
    updated_at  TIMESTAMP     NOT NULL,
    updated_by  CHAR(36)      NOT NULL REFERENCES users (id),
    UNIQUE (talk_id, event_id)
);
CREATE INDEX external_proposals_talk_id_idx ON external_proposals (talk_id);
CREATE INDEX external_proposals_event_id_idx ON external_proposals (event_id);
CREATE INDEX external_proposals_status_idx ON external_proposals (status);

CREATE INDEX talks_status_idx ON talks (status);
CREATE INDEX proposals_status_idx ON proposals (status);

INSERT INTO external_events
SELECT id,
       name,
       logo,
       description,
       event_start,
       event_finish,
       location,
       location_id,
       location_lat,
       location_lng,
       location_locality,
       location_country,
       event_url,
       tickets_url,
       videos_url,
       twitter_account,
       twitter_hashtag,
       tags,
       created_at,
       created_by,
       updated_at,
       updated_by
FROM external_cfps;

ALTER TABLE external_cfps
    ADD COLUMN event_id CHAR(36) REFERENCES external_events (id);

UPDATE external_cfps
SET event_id=id;

ALTER TABLE external_cfps
    ALTER COLUMN event_id SET NOT NULL;

ALTER TABLE external_cfps
    DROP COLUMN name;
ALTER TABLE external_cfps
    DROP COLUMN logo;
ALTER TABLE external_cfps
    DROP COLUMN event_start;
ALTER TABLE external_cfps
    DROP COLUMN event_finish;
ALTER TABLE external_cfps
    DROP COLUMN event_url;
ALTER TABLE external_cfps
    DROP COLUMN location;
ALTER TABLE external_cfps
    DROP COLUMN location_id;
ALTER TABLE external_cfps
    DROP COLUMN location_lat;
ALTER TABLE external_cfps
    DROP COLUMN location_lng;
ALTER TABLE external_cfps
    DROP COLUMN location_locality;
ALTER TABLE external_cfps
    DROP COLUMN location_country;
ALTER TABLE external_cfps
    DROP COLUMN tickets_url;
ALTER TABLE external_cfps
    DROP COLUMN videos_url;
ALTER TABLE external_cfps
    DROP COLUMN twitter_account;
ALTER TABLE external_cfps
    DROP COLUMN twitter_hashtag;
ALTER TABLE external_cfps
    DROP COLUMN tags;


ALTER TABLE proposals
    ADD COLUMN message VARCHAR(4096) NOT NULL DEFAULT '';
ALTER TABLE talks
    ADD COLUMN message VARCHAR(4096) NOT NULL DEFAULT '';
