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
    id              CHAR(36)     NOT NULL PRIMARY KEY,
    slug            VARCHAR(120) NOT NULL UNIQUE,
    first_name      VARCHAR(30)  NOT NULL,
    last_name       VARCHAR(30)  NOT NULL,
    email           VARCHAR(100) NOT NULL UNIQUE,
    email_validated TIMESTAMP,
    avatar          VARCHAR(200) NOT NULL,
    avatar_source   VARCHAR(20)  NOT NULL,
    public          BOOLEAN      NOT NULL,
    created         TIMESTAMP    NOT NULL,
    updated         TIMESTAMP    NOT NULL
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

CREATE TABLE requests
(
    id       CHAR(36)    NOT NULL,
    kind     VARCHAR(30) NOT NULL,
    email    VARCHAR(100),
    user_id  CHAR(36) REFERENCES users (id),
    deadline TIMESTAMP   NOT NULL,
    created  TIMESTAMP   NOT NULL,
    accepted TIMESTAMP,
    rejected TIMESTAMP
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
    tags        VARCHAR(100)  NOT NULL, -- 5 tags max
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
    description VARCHAR(2048) NOT NULL,
    owners      VARCHAR(369)  NOT NULL, -- 10 owners max
    public      BOOLEAN       NOT NULL,
    tags        VARCHAR(100)  NOT NULL, -- 5 tags max
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
    start       TIMESTAMP,
    "end"       TIMESTAMP,
    description VARCHAR(2048) NOT NULL,
    tags        VARCHAR(100)  NOT NULL, -- 5 tags max
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
    description VARCHAR(2048) NOT NULL,
    logo        VARCHAR(200)  NOT NULL,
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
    address         VARCHAR(2048)    NOT NULL,
    address_lat     DOUBLE PRECISION NOT NULL,
    address_lng     DOUBLE PRECISION NOT NULL,
    address_country VARCHAR(20)      NOT NULL,
    description     VARCHAR(2048)    NOT NULL,
    room_size       INT,
    created         TIMESTAMP        NOT NULL,
    created_by      CHAR(36)         NOT NULL REFERENCES users (id),
    updated         TIMESTAMP        NOT NULL,
    updated_by      CHAR(36)         NOT NULL REFERENCES users (id)
);

CREATE TABLE contacts
(
    id         CHAR(36)     NOT NULL PRIMARY KEY,
    partner_id CHAR(36)     NOT NULL REFERENCES partners (id),
    first_name VARCHAR(30)  NOT NULL,
    last_name  VARCHAR(30)  NOT NULL,
    email      VARCHAR(100) NOT NULL,
    role       VARCHAR(30)  NOT NULL,
    created    TIMESTAMP    NOT NULL,
    created_by CHAR(36)     NOT NULL REFERENCES users (id),
    updated    TIMESTAMP    NOT NULL,
    updated_by CHAR(36)     NOT NULL REFERENCES users (id)
);

CREATE TABLE events
(
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    group_id    CHAR(36)      NOT NULL REFERENCES groups (id),
    cfp_id      CHAR(36) REFERENCES cfps (id),
    slug        VARCHAR(120)  NOT NULL,
    name        VARCHAR(120)  NOT NULL,
    start       TIMESTAMP     NOT NULL,
    description VARCHAR(2048) NOT NULL,
    venue       CHAR(36) REFERENCES venues (id),
    talks       VARCHAR(258)  NOT NULL, -- 7 talks max
    tags        VARCHAR(100)  NOT NULL, -- 5 tags max
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
    tags        VARCHAR(100)  NOT NULL, -- 5 tags max
    created     TIMESTAMP     NOT NULL,
    created_by  CHAR(36)      NOT NULL REFERENCES users (id),
    updated     TIMESTAMP     NOT NULL,
    updated_by  CHAR(36)      NOT NULL REFERENCES users (id),
    UNIQUE (talk_id, cfp_id)
);

CREATE TABLE sponsor_packs
(
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    group_id    CHAR(36)      NOT NULL REFERENCES groups (id),
    slug        VARCHAR(120)  NOT NULL,
    name        VARCHAR(120)  NOT NULL,
    description VARCHAR(2048) NOT NULL,
    price       REAL          NOT NULL,
    active      BOOLEAN       NOT NULL,
    created     TIMESTAMP     NOT NULL,
    created_by  CHAR(36)      NOT NULL REFERENCES users (id),
    updated     TIMESTAMP     NOT NULL,
    updated_by  CHAR(36)      NOT NULL REFERENCES users (id)
);

CREATE TABLE sponsors
(
    id              CHAR(36)  NOT NULL PRIMARY KEY,
    group_id        CHAR(36)  NOT NULL REFERENCES groups (id),
    partner_id      CHAR(36)  NOT NULL REFERENCES partners (id),
    sponsor_pack_id CHAR(36)  NOT NULL REFERENCES sponsor_packs (id),
    contact_id      CHAR(36)  NOT NULL REFERENCES contacts (id),
    start           TIMESTAMP NOT NULL,
    "end"           TIMESTAMP,
    paid            TIMESTAMP,
    paid_amount     REAL,
    created         TIMESTAMP NOT NULL,
    created_by      CHAR(36)  NOT NULL REFERENCES users (id),
    updated         TIMESTAMP NOT NULL,
    updated_by      CHAR(36)  NOT NULL REFERENCES users (id)
);

CREATE TABLE settings
(
    target     VARCHAR(30)   NOT NULL, -- enum for the target: group or user
    target_id  CHAR(36)      NOT NULL, -- id for the target
    value      VARCHAR(2048) NOT NULL, -- json serialized settings
    updated    TIMESTAMP     NOT NULL,
    updated_by CHAR(36)      NOT NULL REFERENCES users (id),
    UNIQUE (target, target_id)
);
