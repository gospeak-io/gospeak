CREATE TABLE comments
(
    event_id    CHAR(36) REFERENCES events (id),
    proposal_id CHAR(36) REFERENCES proposals (id),
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    kind        VARCHAR(15)   NOT NULL, -- Event, Proposal, ProposalOrga
    answers     CHAR(36) REFERENCES comments (id),
    text        VARCHAR(4096) NOT NULL,
    created_at  TIMESTAMP     NOT NULL,
    created_by  CHAR(36)      NOT NULL REFERENCES users (id)
);
CREATE INDEX comments_event_idx ON comments (event_id);
CREATE INDEX comments_proposal_idx ON comments (proposal_id);


CREATE TABLE external_cfps
(
    id                CHAR(36)      NOT NULL PRIMARY KEY,
    name              VARCHAR(120)  NOT NULL,
    logo              VARCHAR(1024),
    description       VARCHAR(4096) NOT NULL,
    begin             TIMESTAMP,
    close             TIMESTAMP,
    url               VARCHAR(1024) NOT NULL,
    event_start       TIMESTAMP,
    event_finish      TIMESTAMP,
    event_url         VARCHAR(1024),
    location          VARCHAR(4096),
    location_lat      DOUBLE PRECISION,
    location_lng      DOUBLE PRECISION,
    location_locality VARCHAR(30),
    location_country  VARCHAR(30),
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
CREATE INDEX external_cfps_close_idx ON external_cfps (close);
CREATE INDEX external_cfps_location_lat_idx ON external_cfps (location_lat);
CREATE INDEX external_cfps_location_lng_idx ON external_cfps (location_lng);
