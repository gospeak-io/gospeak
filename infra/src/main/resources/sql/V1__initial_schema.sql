CREATE TABLE users
(
    id              CHAR(36)      NOT NULL PRIMARY KEY,
    slug            VARCHAR(120)  NOT NULL UNIQUE,
    first_name      VARCHAR(120)  NOT NULL,
    last_name       VARCHAR(120)  NOT NULL,
    email           VARCHAR(120)  NOT NULL UNIQUE,
    email_validated TIMESTAMP,
    avatar          VARCHAR(1024) NOT NULL,
    avatar_source   VARCHAR(20)   NOT NULL,
    status          VARCHAR(10)   NOT NULL,
    bio             VARCHAR(4096),
    company         VARCHAR(36),
    location        VARCHAR(36),
    twitter         VARCHAR(1024),
    linkedin        VARCHAR(1024),
    phone           VARCHAR(36),
    website         VARCHAR(1024),
    created         TIMESTAMP     NOT NULL,
    updated         TIMESTAMP     NOT NULL
);

CREATE TABLE credentials
(
    provider_id  VARCHAR(30)  NOT NULL,
    provider_key VARCHAR(100) NOT NULL,
    hasher       VARCHAR(100) NOT NULL,
    password     VARCHAR(100) NOT NULL,
    salt         VARCHAR(100),
    PRIMARY KEY (provider_id, provider_key)
);

CREATE TABLE logins
(
    provider_id  VARCHAR(30)  NOT NULL,
    provider_key VARCHAR(100) NOT NULL,
    user_id      CHAR(36)     NOT NULL REFERENCES users (id),
    PRIMARY KEY (provider_id, provider_key)
);

CREATE TABLE talks
(
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    slug        VARCHAR(120)  NOT NULL UNIQUE,
    status      VARCHAR(10)   NOT NULL,
    title       VARCHAR(120)  NOT NULL,
    duration    BIGINT        NOT NULL,
    description VARCHAR(4096) NOT NULL,
    speakers    VARCHAR(184)  NOT NULL, -- 5 speakers max
    slides      VARCHAR(1024),
    video       VARCHAR(1024),
    tags        VARCHAR(150)  NOT NULL, -- 5 tags max
    created     TIMESTAMP     NOT NULL,
    created_by  CHAR(36)      NOT NULL REFERENCES users (id),
    updated     TIMESTAMP     NOT NULL,
    updated_by  CHAR(36)      NOT NULL REFERENCES users (id)
);

CREATE TABLE groups
(
    id                CHAR(36)      NOT NULL PRIMARY KEY,
    slug              VARCHAR(120)  NOT NULL UNIQUE,
    name              VARCHAR(120)  NOT NULL,
    logo              VARCHAR(1024),
    banner            VARCHAR(1024),
    contact           VARCHAR(120),           -- group email address
    website           VARCHAR(1024),
    description       VARCHAR(4096) NOT NULL,
    location          VARCHAR(4096),
    location_lat      DOUBLE PRECISION,
    location_lng      DOUBLE PRECISION,
    location_country  VARCHAR(30),
    owners            VARCHAR(369)  NOT NULL, -- 10 owners max
    social_facebook   VARCHAR(1024),
    social_instagram  VARCHAR(1024),
    social_twitter    VARCHAR(1024),
    social_linkedIn   VARCHAR(1024),
    social_youtube    VARCHAR(1024),
    social_meetup     VARCHAR(1024),
    social_eventbrite VARCHAR(1024),
    social_slack      VARCHAR(1024),
    social_discord    VARCHAR(1024),
    tags              VARCHAR(150)  NOT NULL, -- 5 tags max
    status            VARCHAR(10)   NOT NULL,
    created           TIMESTAMP     NOT NULL,
    created_by        CHAR(36)      NOT NULL REFERENCES users (id),
    updated           TIMESTAMP     NOT NULL,
    updated_by        CHAR(36)      NOT NULL REFERENCES users (id)
);

CREATE TABLE group_settings
(
    group_id                CHAR(36) PRIMARY KEY REFERENCES groups (id),
    meetup_access_token     VARCHAR(200),       -- encrypted
    meetup_refresh_token    VARCHAR(200),       -- encrypted
    meetup_group_slug       VARCHAR(120),
    meetup_logged_user_id   BIGINT,
    meetup_logged_user_name VARCHAR(120),
    slack_token             VARCHAR(200),       -- encrypted
    slack_bot_name          VARCHAR(120),
    slack_bot_avatar        VARCHAR(1024),
    event_description       VARCHAR   NOT NULL,
    event_templates         VARCHAR   NOT NULL, -- json serialized Map[String, MustacheTextTmpl[TemplateData.EventInfo]]
    actions                 VARCHAR   NOT NULL, -- json serialized Map[Group.Settings.Action.Trigger, Seq[Group.Settings.Action]]
    updated                 TIMESTAMP NOT NULL,
    updated_by              CHAR(36)  NOT NULL REFERENCES users (id)
);

CREATE TABLE cfps
(
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    group_id    CHAR(36)      NOT NULL REFERENCES groups (id),
    slug        VARCHAR(120)  NOT NULL UNIQUE,
    name        VARCHAR(120)  NOT NULL,
    begin       TIMESTAMP,
    close       TIMESTAMP,
    description VARCHAR(4096) NOT NULL,
    tags        VARCHAR(150)  NOT NULL, -- 5 tags max
    created     TIMESTAMP     NOT NULL,
    created_by  CHAR(36)      NOT NULL REFERENCES users (id),
    updated     TIMESTAMP     NOT NULL,
    updated_by  CHAR(36)      NOT NULL REFERENCES users (id)
);

CREATE TABLE partners
(
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    group_id    CHAR(36)      NOT NULL REFERENCES groups (id),
    slug        VARCHAR(120)  NOT NULL,
    name        VARCHAR(120)  NOT NULL,
    notes       VARCHAR(4096) NOT NULL,
    description VARCHAR(4096),
    logo        VARCHAR(1024) NOT NULL,
    twitter     VARCHAR(1024),
    created     TIMESTAMP     NOT NULL,
    created_by  CHAR(36)      NOT NULL REFERENCES users (id),
    updated     TIMESTAMP     NOT NULL,
    updated_by  CHAR(36)      NOT NULL REFERENCES users (id),
    UNIQUE (group_id, slug)
);

CREATE TABLE contacts
(
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    partner_id  CHAR(36)      NOT NULL REFERENCES partners (id),
    first_name  VARCHAR(120)  NOT NULL,
    last_name   VARCHAR(120)  NOT NULL,
    email       VARCHAR(120)  NOT NULL,
    description VARCHAR(4096) NOT NULL,
    created     TIMESTAMP     NOT NULL,
    created_by  CHAR(36)      NOT NULL REFERENCES users (id),
    updated     TIMESTAMP     NOT NULL,
    updated_by  CHAR(36)      NOT NULL REFERENCES users (id)
);

CREATE TABLE venues
(
    id              CHAR(36)         NOT NULL PRIMARY KEY,
    partner_id      CHAR(36)         NOT NULL REFERENCES partners (id),
    contact_id      CHAR(36) REFERENCES contacts (id),
    address         VARCHAR(4096)    NOT NULL,
    address_lat     DOUBLE PRECISION NOT NULL,
    address_lng     DOUBLE PRECISION NOT NULL,
    address_country VARCHAR(30)      NOT NULL,
    description     VARCHAR(4096)    NOT NULL,
    room_size       INT,
    meetupGroup     VARCHAR(80),
    meetupVenue     BIGINT,
    created         TIMESTAMP        NOT NULL,
    created_by      CHAR(36)         NOT NULL REFERENCES users (id),
    updated         TIMESTAMP        NOT NULL,
    updated_by      CHAR(36)         NOT NULL REFERENCES users (id)
);

CREATE TABLE events
(
    id                    CHAR(36)      NOT NULL PRIMARY KEY,
    group_id              CHAR(36)      NOT NULL REFERENCES groups (id),
    cfp_id                CHAR(36) REFERENCES cfps (id),
    slug                  VARCHAR(120)  NOT NULL,
    name                  VARCHAR(120)  NOT NULL,
    start                 TIMESTAMP     NOT NULL,
    max_attendee          INT,
    allow_rsvp            BOOLEAN       NOT NULL,
    description           VARCHAR(4096) NOT NULL,
    orga_notes            VARCHAR(4096) NOT NULL,
    orga_notes_updated_at TIMESTAMP     NOT NULL,
    orga_notes_updated_by CHAR(36)      NOT NULL REFERENCES users (id),
    venue                 CHAR(36) REFERENCES venues (id),
    talks                 VARCHAR(258)  NOT NULL, -- 7 talks max
    tags                  VARCHAR(150)  NOT NULL, -- 5 tags max
    published             TIMESTAMP,
    meetupGroup           VARCHAR(80),
    meetupEvent           BIGINT,
    created               TIMESTAMP     NOT NULL,
    created_by            CHAR(36)      NOT NULL REFERENCES users (id),
    updated               TIMESTAMP     NOT NULL,
    updated_by            CHAR(36)      NOT NULL REFERENCES users (id),
    UNIQUE (group_id, slug)
);

CREATE TABLE proposals
(
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    talk_id     CHAR(36)      NOT NULL REFERENCES talks (id),
    cfp_id      CHAR(36)      NOT NULL REFERENCES cfps (id),
    event_id    CHAR(36) REFERENCES events (id),
    status      VARCHAR(10)   NOT NULL,
    title       VARCHAR(120)  NOT NULL,
    duration    BIGINT        NOT NULL,
    description VARCHAR(4096) NOT NULL,
    speakers    VARCHAR(184)  NOT NULL, -- 5 speakers max
    slides      VARCHAR(1024),
    video       VARCHAR(1024),
    tags        VARCHAR(150)  NOT NULL, -- 5 tags max
    created     TIMESTAMP     NOT NULL,
    created_by  CHAR(36)      NOT NULL REFERENCES users (id),
    updated     TIMESTAMP     NOT NULL,
    updated_by  CHAR(36)      NOT NULL REFERENCES users (id),
    UNIQUE (talk_id, cfp_id)
);

CREATE TABLE proposal_ratings
(
    proposal_id CHAR(36)  NOT NULL REFERENCES proposals (id),
    grade       INT       NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    created_by  CHAR(36)  NOT NULL REFERENCES users (id),
    UNIQUE (proposal_id, created_by)
);

CREATE TABLE sponsor_packs
(
    id          CHAR(36)         NOT NULL PRIMARY KEY,
    group_id    CHAR(36)         NOT NULL REFERENCES groups (id),
    slug        VARCHAR(120)     NOT NULL,
    name        VARCHAR(120)     NOT NULL,
    description VARCHAR(4096)    NOT NULL,
    price       DOUBLE PRECISION NOT NULL,
    currency    VARCHAR(10)      NOT NULL,
    duration    VARCHAR(20)      NOT NULL,
    active      BOOLEAN          NOT NULL,
    created     TIMESTAMP        NOT NULL,
    created_by  CHAR(36)         NOT NULL REFERENCES users (id),
    updated     TIMESTAMP        NOT NULL,
    updated_by  CHAR(36)         NOT NULL REFERENCES users (id),
    UNIQUE (group_id, slug)
);

CREATE TABLE sponsors
(
    id              CHAR(36)         NOT NULL PRIMARY KEY,
    group_id        CHAR(36)         NOT NULL REFERENCES groups (id),
    partner_id      CHAR(36)         NOT NULL REFERENCES partners (id),
    sponsor_pack_id CHAR(36)         NOT NULL REFERENCES sponsor_packs (id),
    contact_id      CHAR(36) REFERENCES contacts (id),
    start           DATE             NOT NULL,
    finish          DATE             NOT NULL,
    paid            DATE,
    price           DOUBLE PRECISION NOT NULL,
    currency        VARCHAR(10)      NOT NULL,
    created         TIMESTAMP        NOT NULL,
    created_by      CHAR(36)         NOT NULL REFERENCES users (id),
    updated         TIMESTAMP        NOT NULL,
    updated_by      CHAR(36)         NOT NULL REFERENCES users (id)
);

CREATE TABLE group_members
(
    group_id     CHAR(36)    NOT NULL REFERENCES groups (id),
    user_id      CHAR(36)    NOT NULL REFERENCES users (id),
    role         VARCHAR(10) NOT NULL, -- Owner, Member
    presentation VARCHAR(4096),
    joined_at    TIMESTAMP   NOT NULL,
    leaved_at    TIMESTAMP,
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE event_rsvps
(
    event_id    CHAR(36)    NOT NULL REFERENCES events (id),
    user_id     CHAR(36)    NOT NULL REFERENCES users (id),
    answer      VARCHAR(10) NOT NULL, -- Yes, No, Wait
    answered_at TIMESTAMP   NOT NULL,
    PRIMARY KEY (event_id, user_id)
);

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

CREATE TABLE requests
(
    id          CHAR(36)    NOT NULL,
    kind        VARCHAR(30) NOT NULL,
    group_id    CHAR(36) REFERENCES groups (id),
    talk_id     CHAR(36) REFERENCES talks (id),
    proposal_id CHAR(36) REFERENCES proposals (id),
    email       VARCHAR(120),
    deadline    TIMESTAMP,
    created     TIMESTAMP   NOT NULL,
    created_by  CHAR(36) REFERENCES users (id),
    accepted    TIMESTAMP,
    accepted_by CHAR(36) REFERENCES users (id),
    rejected    TIMESTAMP,
    rejected_by CHAR(36) REFERENCES users (id),
    canceled    TIMESTAMP,
    canceled_by CHAR(36) REFERENCES users (id)
);

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
