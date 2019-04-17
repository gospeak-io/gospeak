CREATE TABLE users
(
    id              CHAR(36)     NOT NULL PRIMARY KEY,
    slug            VARCHAR(30)  NOT NULL UNIQUE,
    first_name      VARCHAR(30)  NOT NULL,
    last_name       VARCHAR(30)  NOT NULL,
    email           VARCHAR(100) NOT NULL UNIQUE,
    email_validated TIMESTAMP,
    avatar          VARCHAR(150) NOT NULL,
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
    slug        VARCHAR(30)   NOT NULL UNIQUE,
    title       VARCHAR(100)  NOT NULL,
    duration    BIGINT        NOT NULL,
    status      VARCHAR(10)   NOT NULL,
    description VARCHAR(2048) NOT NULL,
    speakers    VARCHAR(184)  NOT NULL,
    slides      VARCHAR(1024),
    video       VARCHAR(1024),
    created     TIMESTAMP     NOT NULL,
    created_by  CHAR(36)      NOT NULL REFERENCES users (id),
    updated     TIMESTAMP     NOT NULL,
    updated_by  CHAR(36)      NOT NULL REFERENCES users (id)
);

CREATE TABLE groups
(
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    slug        VARCHAR(30)   NOT NULL UNIQUE,
    name        VARCHAR(100)  NOT NULL,
    description VARCHAR(2048) NOT NULL,
    owners      VARCHAR(184)  NOT NULL,
    public      BOOLEAN       NOT NULL,
    created     TIMESTAMP     NOT NULL,
    created_by  CHAR(36)      NOT NULL REFERENCES users (id),
    updated     TIMESTAMP     NOT NULL,
    updated_by  CHAR(36)      NOT NULL REFERENCES users (id)
);

CREATE TABLE cfps
(
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    group_id    CHAR(36)      NOT NULL REFERENCES groups (id),
    slug        VARCHAR(30)   NOT NULL UNIQUE,
    name        VARCHAR(100)  NOT NULL,
    start       TIMESTAMP,
    "end"       TIMESTAMP,
    description VARCHAR(2048) NOT NULL,
    created     TIMESTAMP     NOT NULL,
    created_by  CHAR(36)      NOT NULL REFERENCES users (id),
    updated     TIMESTAMP     NOT NULL,
    updated_by  CHAR(36)      NOT NULL REFERENCES users (id)
);

CREATE TABLE events
(
    id          CHAR(36)     NOT NULL PRIMARY KEY,
    group_id    CHAR(36)     NOT NULL REFERENCES groups (id),
    cfp_id      CHAR(36) REFERENCES cfps (id),
    slug        VARCHAR(30)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    start       TIMESTAMP    NOT NULL,
    description VARCHAR(2048),
    venue       VARCHAR(2048),
    talks       VARCHAR(184) NOT NULL,
    created     TIMESTAMP    NOT NULL,
    created_by  CHAR(36)     NOT NULL REFERENCES users (id),
    updated     TIMESTAMP    NOT NULL,
    updated_by  CHAR(36)     NOT NULL REFERENCES users (id),
    UNIQUE (group_id, slug)
);

CREATE TABLE proposals
(
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    talk_id     CHAR(36)      NOT NULL REFERENCES talks (id),
    cfp_id      CHAR(36)      NOT NULL REFERENCES cfps (id),
    event_id    CHAR(36) REFERENCES events (id),
    title       VARCHAR(100)  NOT NULL,
    duration    BIGINT        NOT NULL,
    status      VARCHAR(10)   NOT NULL,
    description VARCHAR(2048) NOT NULL,
    speakers    VARCHAR(184)  NOT NULL,
    slides      VARCHAR(1024),
    video       VARCHAR(1024),
    created     TIMESTAMP     NOT NULL,
    created_by  CHAR(36)      NOT NULL REFERENCES users (id),
    updated     TIMESTAMP     NOT NULL,
    updated_by  CHAR(36)      NOT NULL REFERENCES users (id),
    UNIQUE (talk_id, cfp_id)
);
