CREATE TABLE videos
(
    platform      VARCHAR(10)   NOT NULL, -- enum: youtube, vimeo or undefined
    url           VARCHAR(1024) NOT NULL, -- ex: https://www.youtube.com/watch?v=GigiViV-GFk / https://vimeo.com/339718355
    id            VARCHAR(15)   NOT NULL, -- ex: GigiViV-GFk                                 / 339718355
    channel_id    VARCHAR(30)   NOT NULL, -- ex: UCqU8dtoFv2r0cA795pHvtOw                    / mixitconf
    channel_name  VARCHAR(120)  NOT NULL, -- ex: Best of Web                                 / MiXiT
    playlist_id   VARCHAR(40),            -- ex: PLP0qkoIjGNDW4ZVCO7exKipCXP-GRA0Ym          / 6037034
    playlist_name VARCHAR(120),           -- ex: Best of Web 2019                            / MiXiT 2019
    title         VARCHAR(120)  NOT NULL,
    description   VARCHAR(4096) NOT NULL,
    tags          VARCHAR(150)  NOT NULL,
    published_at  TIMESTAMP     NOT NULL,
    duration      BIGINT        NOT NULL,
    lang          VARCHAR(2)    NOT NULL,
    views         BIGINT        NOT NULL,
    likes         BIGINT        NOT NULL,
    dislikes      BIGINT        NOT NULL,
    comments      BIGINT        NOT NULL,
    updated_at    TIMESTAMP     NOT NULL, -- last time video data were updated
    UNIQUE (id),
    UNIQUE (url)
);
CREATE INDEX videos_id_idx ON videos (id);
CREATE INDEX videos_title_idx ON videos (title);
CREATE INDEX videos_channel_id_idx ON videos (channel_id);
CREATE INDEX videos_channel_name_idx ON videos (channel_name);
CREATE INDEX videos_playlist_id_idx ON videos (playlist_id);
CREATE INDEX videos_playlist_name_idx ON videos (playlist_name);

CREATE TABLE video_sources
(
    video_id             VARCHAR(15) NOT NULL REFERENCES videos (id),
    talk_id              CHAR(36) REFERENCES talks (id),
    proposal_id          CHAR(36) REFERENCES proposals (id),
    external_proposal_id CHAR(36) REFERENCES external_proposals (id),
    external_event_id    CHAR(36) REFERENCES external_events (id),
    UNIQUE (video_id, talk_id),
    UNIQUE (video_id, proposal_id),
    UNIQUE (video_id, external_proposal_id),
    UNIQUE (video_id, external_event_id)
);
