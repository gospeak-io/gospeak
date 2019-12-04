CREATE TABLE companies
(
    id         CHAR(36)     NOT NULL PRIMARY KEY,
    owners     VARCHAR(369) NOT NULL, -- 10 owners max
    created_at TIMESTAMP    NOT NULL,
    created_by CHAR(36)     NOT NULL REFERENCES users (id),
    updated_at TIMESTAMP    NOT NULL,
    updated_by CHAR(36)     NOT NULL REFERENCES users (id)
);
