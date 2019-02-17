CREATE TABLE users (
  id         CHAR(36)     NOT NULL PRIMARY KEY,
  slug       VARCHAR(30)  NOT NULL UNIQUE,
  first_name VARCHAR(30)  NOT NULL,
  last_name  VARCHAR(30)  NOT NULL,
  email      VARCHAR(100) NOT NULL UNIQUE,
  created    TIMESTAMP    NOT NULL,
  updated    TIMESTAMP    NOT NULL
);

CREATE TABLE talks (
  id          CHAR(36)      NOT NULL PRIMARY KEY,
  slug        VARCHAR(30)   NOT NULL,
  title       VARCHAR(100)  NOT NULL,
  duration    BIGINT        NOT NULL,
  status      VARCHAR(10)   NOT NULL,
  description VARCHAR(2048) NOT NULL,
  speakers    VARCHAR(184)  NOT NULL,
  slides      VARCHAR(1024),
  video       VARCHAR(1024),
  created     TIMESTAMP     NOT NULL,
  created_by  CHAR(36)      NOT NULL REFERENCES users(id),
  updated     TIMESTAMP     NOT NULL,
  updated_by  CHAR(36)      NOT NULL REFERENCES users(id)
);

CREATE TABLE groups (
  id          CHAR(36)      NOT NULL PRIMARY KEY,
  slug        VARCHAR(30)   NOT NULL UNIQUE,
  name        VARCHAR(100)  NOT NULL,
  description VARCHAR(2048) NOT NULL,
  owners      VARCHAR(184)  NOT NULL,
  created     TIMESTAMP     NOT NULL,
  created_by  CHAR(36)      NOT NULL REFERENCES users(id),
  updated     TIMESTAMP     NOT NULL,
  updated_by  CHAR(36)      NOT NULL REFERENCES users(id)
);

CREATE TABLE events (
  group_id    CHAR(36)      NOT NULL REFERENCES groups(id),
  id          CHAR(36)      NOT NULL PRIMARY KEY,
  slug        VARCHAR(30)   NOT NULL,
  name        VARCHAR(100)  NOT NULL,
  start       TIMESTAMP     NOT NULL,
  description VARCHAR(2048),
  venue       VARCHAR(2048),
  talks       VARCHAR(184)  NOT NULL,
  created     TIMESTAMP     NOT NULL,
  created_by  CHAR(36)      NOT NULL REFERENCES users(id),
  updated     TIMESTAMP     NOT NULL,
  updated_by  CHAR(36)      NOT NULL REFERENCES users(id),
  UNIQUE (group_id, slug)
);

CREATE TABLE cfps (
  id          CHAR(36)      NOT NULL PRIMARY KEY,
  slug        VARCHAR(30)   NOT NULL UNIQUE,
  name        VARCHAR(100)  NOT NULL,
  description VARCHAR(2048) NOT NULL,
  group_id    CHAR(36)      NOT NULL UNIQUE REFERENCES groups(id),
  created     TIMESTAMP     NOT NULL,
  created_by  CHAR(36)      NOT NULL REFERENCES users(id),
  updated     TIMESTAMP     NOT NULL,
  updated_by  CHAR(36)      NOT NULL REFERENCES users(id)
);

CREATE TABLE proposals (
  id          CHAR(36)      NOT NULL PRIMARY KEY,
  talk_id     CHAR(36)      NOT NULL REFERENCES talks(id),
  cfp_id      CHAR(36)      NOT NULL REFERENCES cfps(id),
  event_id    CHAR(36)               REFERENCES events(id),
  title       VARCHAR(100)  NOT NULL,
  duration    BIGINT        NOT NULL,
  status      VARCHAR(10)   NOT NULL,
  description VARCHAR(2048) NOT NULL,
  speakers    VARCHAR(184)  NOT NULL,
  slides      VARCHAR(1024),
  video       VARCHAR(1024),
  created     TIMESTAMP     NOT NULL,
  created_by  CHAR(36)      NOT NULL REFERENCES users(id),
  updated     TIMESTAMP     NOT NULL,
  updated_by  CHAR(36)      NOT NULL REFERENCES users(id),
  UNIQUE (talk_id, cfp_id)
);
