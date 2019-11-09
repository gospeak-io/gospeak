CREATE TABLE external_cfps
(
    id               CHAR(36)      NOT NULL PRIMARY KEY,
    name             VARCHAR(120)  NOT NULL,
    logo             VARCHAR(1024),
    description      VARCHAR(4096) NOT NULL,
    begin            TIMESTAMP,
    close            TIMESTAMP,
    url              VARCHAR(1024) NOT NULL,
    event_start      TIMESTAMP,
    event_finish     TIMESTAMP,
    event_url        VARCHAR(1024),
    address          VARCHAR(4096),
    address_lat      DOUBLE PRECISION,
    address_lng      DOUBLE PRECISION,
    address_locality VARCHAR(30),
    address_country  VARCHAR(30),
    tickets_url      VARCHAR(1024),
    videos_url       VARCHAR(1024),
    twitter_account  VARCHAR(120),
    twitter_hashtag  VARCHAR(120),
    tags             VARCHAR(150)  NOT NULL, -- 5 tags max
    created_at       TIMESTAMP     NOT NULL,
    created_by       CHAR(36)      NOT NULL REFERENCES users (id),
    updated_at       TIMESTAMP     NOT NULL,
    updated_by       CHAR(36)      NOT NULL REFERENCES users (id)
);
CREATE INDEX external_cfps_close_idx ON external_cfps (close);
CREATE INDEX external_cfps_address_lat_idx ON external_cfps (address_lat);
CREATE INDEX external_cfps_address_lng_idx ON external_cfps (address_lng);
