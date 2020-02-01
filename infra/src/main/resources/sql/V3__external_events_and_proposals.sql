CREATE TABLE external_events
(
    id                CHAR(36)      NOT NULL PRIMARY KEY,
    name              VARCHAR(120)  NOT NULL UNIQUE,
    logo              VARCHAR(1024),
    description       VARCHAR(4096) NOT NULL,
    start             TIMESTAMP     NOT NULL,
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
    title       VARCHAR(120)  NOT NULL,
    duration    BIGINT        NOT NULL,
    description VARCHAR(4096) NOT NULL,
    speakers    VARCHAR(184)  NOT NULL, -- 5 speakers max
    slides      VARCHAR(1024),
    video       VARCHAR(1024),
    tags        VARCHAR(150)  NOT NULL, -- 5 tags max
    created_at  TIMESTAMP     NOT NULL,
    created_by  CHAR(36)      NOT NULL REFERENCES users (id),
    updated_at  TIMESTAMP     NOT NULL,
    updated_by  CHAR(36)      NOT NULL REFERENCES users (id),
    UNIQUE (talk_id, event_id)
);
CREATE INDEX external_proposals_talk_id_idx ON external_proposals (talk_id);
CREATE INDEX external_proposals_event_id_idx ON external_proposals (event_id);

/* TODO: update external CFP to link to an event */
