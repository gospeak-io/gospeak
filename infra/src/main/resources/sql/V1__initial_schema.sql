--
--            user<---login/credentials
--             ^
--             |
--  +----------+-------------+
--  |          |             |
--  |          |           group
--  |          |             ^
--  |          |             |
--  |          |      +------+--------------+-----------+
--  |          |      |      |              |           |
--  |          +---->cfp     |           partner   sponsor_pack
--  |          |      ^      |              ^           ^
--  |          |      |      |              |           |
--  |          |      |      |      +-------+-----+     |
--  |          |      |      |      |       |     |     |
--  |          |      |      |    venue  contact  |     |
--  |          |      |      |      ^       ^     |     |
--  |          |      |      |      |       |     |     |
--  |          |      +------+------+       +-----+-----+
--  |          |             |                    |
-- talk <-- proposal <---> event               sponsor
--
-- others:
--   - requests
--   - settings

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
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    slug        VARCHAR(120)  NOT NULL UNIQUE,
    name        VARCHAR(120)  NOT NULL,
    contact     VARCHAR(120),           -- group email address
    description VARCHAR(4096) NOT NULL,
    owners      VARCHAR(369)  NOT NULL, -- 10 owners max
    tags        VARCHAR(150)  NOT NULL, -- 5 tags max
    created     TIMESTAMP     NOT NULL,
    created_by  CHAR(36)      NOT NULL REFERENCES users (id),
    updated     TIMESTAMP     NOT NULL,
    updated_by  CHAR(36)      NOT NULL REFERENCES users (id)
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

CREATE TABLE venues
(
    id              CHAR(36)         NOT NULL PRIMARY KEY,
    partner_id      CHAR(36)         NOT NULL REFERENCES partners (id),
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

CREATE TABLE events
(
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    group_id    CHAR(36)      NOT NULL REFERENCES groups (id),
    cfp_id      CHAR(36) REFERENCES cfps (id),
    slug        VARCHAR(120)  NOT NULL,
    name        VARCHAR(120)  NOT NULL,
    start       TIMESTAMP     NOT NULL,
    description VARCHAR(4096) NOT NULL,
    venue       CHAR(36) REFERENCES venues (id),
    talks       VARCHAR(258)  NOT NULL, -- 7 talks max
    tags        VARCHAR(150)  NOT NULL, -- 5 tags max
    published   TIMESTAMP,
    meetupGroup VARCHAR(80),
    meetupEvent BIGINT,
    created     TIMESTAMP     NOT NULL,
    created_by  CHAR(36)      NOT NULL REFERENCES users (id),
    updated     TIMESTAMP     NOT NULL,
    updated_by  CHAR(36)      NOT NULL REFERENCES users (id),
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
    -- contact_id      CHAR(36) REFERENCES contacts (id),
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

CREATE TABLE group_settings
(
    group_id                CHAR(36) PRIMARY KEY REFERENCES groups (id),
    meetup_access_token     CHAR(67),
    meetup_refresh_token    CHAR(67),
    meetup_group_slug       CHAR(120),
    meetup_logged_user_id   BIGINT,
    meetup_logged_user_name CHAR(120),
    slack_token             CHAR(74),
    slack_bot_name          CHAR(120),
    slack_bot_avatar        CHAR(1024),
    event_description       VARCHAR   NOT NULL,
    event_templates         VARCHAR   NOT NULL, -- json serialized Map[String, MustacheTextTmpl[TemplateData.EventInfo]]
    actions                 VARCHAR   NOT NULL, -- json serialized Map[Group.Settings.Action.Trigger, Seq[Group.Settings.Action]]
    updated                 TIMESTAMP NOT NULL,
    updated_by              CHAR(36)  NOT NULL REFERENCES users (id)
);
