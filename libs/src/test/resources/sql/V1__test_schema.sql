CREATE TABLE users
(
    id    INT         NOT NULL PRIMARY KEY,
    name  VARCHAR(50) NOT NULL,
    email VARCHAR(50)
);

INSERT INTO users (id, name, email)
VALUES (1, 'loic', 'loic@mail.com'),
       (2, 'jean', null),
       (3, 'tim', 'tim@mail.com');

CREATE TABLE categories
(
    id   INT         NOT NULL PRIMARY KEY,
    name VARCHAR(50) NOT NULL
);

INSERT INTO categories (id, name)
VALUES (1, 'Tech'),
       (2, 'Political');

CREATE TABLE posts
(
    id       INT           NOT NULL PRIMARY KEY,
    title    VARCHAR(50)   NOT NULL,
    text     VARCHAR(4096) NOT NULL,
    date     TIMESTAMP     NOT NULL,
    author   INT           NOT NULL REFERENCES users (id),
    category INT REFERENCES categories (id)
);

INSERT INTO posts (id, title, text, date, author, category)
VALUES (1, 'Happy new year', 'The awful year', TIMESTAMP '2019-12-31 23:59:00', 1, null),
       (2, 'First 2020 post', 'bla bla', TIMESTAMP '2020-01-01 12:00:00', 1, null),
       (3, 'SQL Queries', 'Using jOOQ and Doobie', TIMESTAMP '2020-07-18 16:32:00', 2, 1);

CREATE TABLE featured
(
    post_id INT       NOT NULL REFERENCES posts (id),
    by      INT       NOT NULL REFERENCES users (id),
    start   TIMESTAMP NOT NULL,
    stop    TIMESTAMP NOT NULL,
    UNIQUE (post_id, by)
);

CREATE TABLE kinds
(
    char        CHAR(4),
    varchar     VARCHAR(50),
    timestamp   TIMESTAMP,
    date        DATE,
    boolean     BOOLEAN,
    int         INT,
    bigint      BIGINT,
    double      DOUBLE PRECISION,
    a_long_name INT
);

INSERT INTO kinds (char, varchar, timestamp, date, boolean, int, bigint, double, a_long_name)
VALUES ('char', 'varchar', TIMESTAMP '2020-08-05 10:20:00', DATE '2020-08-05', TRUE, 1, 10, 4.5, 0);
