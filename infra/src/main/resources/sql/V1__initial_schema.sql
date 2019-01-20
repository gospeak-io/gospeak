CREATE TABLE users (
  id         CHAR(36)     NOT NULL PRIMARY KEY,
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
  description VARCHAR(2048) NOT NULL,
  speakers    VARCHAR(184)  NOT NULL,
  created     TIMESTAMP     NOT NULL,
  created_by  CHAR(36)      NOT NULL,
  updated     TIMESTAMP     NOT NULL,
  updated_by  CHAR(36)      NOT NULL
);

CREATE TABLE groups (
  id          CHAR(36)      NOT NULL PRIMARY KEY,
  slug        VARCHAR(30)   NOT NULL,
  name        VARCHAR(100)  NOT NULL,
  description VARCHAR(2048) NOT NULL,
  owners      VARCHAR(184)  NOT NULL,
  created     TIMESTAMP     NOT NULL,
  created_by  CHAR(36)      NOT NULL,
  updated     TIMESTAMP     NOT NULL,
  updated_by  CHAR(36)      NOT NULL
);

CREATE TABLE events (
  group_id    CHAR(36)      NOT NULL,
  id          CHAR(36)      NOT NULL PRIMARY KEY,
  slug        VARCHAR(30)   NOT NULL,
  name        VARCHAR(100)  NOT NULL,
  description VARCHAR(2048),
  venue       VARCHAR(2048),
  talks       VARCHAR(184)  NOT NULL,
  created     TIMESTAMP     NOT NULL,
  created_by  CHAR(36)      NOT NULL,
  updated     TIMESTAMP     NOT NULL,
  updated_by  CHAR(36)      NOT NULL
);

CREATE TABLE proposals (
  id          CHAR(36)      NOT NULL PRIMARY KEY,
  talk_id     CHAR(36)      NOT NULL,
  group_id    CHAR(36)      NOT NULL,
  title       VARCHAR(100)  NOT NULL,
  description VARCHAR(2048) NOT NULL,
  created     TIMESTAMP     NOT NULL,
  created_by  CHAR(36)      NOT NULL,
  updated     TIMESTAMP     NOT NULL,
  updated_by  CHAR(36)      NOT NULL
);
