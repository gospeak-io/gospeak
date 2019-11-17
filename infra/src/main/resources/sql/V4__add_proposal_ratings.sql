CREATE TABLE proposal_ratings
(
    proposal_id CHAR(36)  NOT NULL REFERENCES proposals (id),
    grade       INT       NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    created_by  CHAR(36)  NOT NULL REFERENCES users (id),
    UNIQUE (proposal_id, created_by)
);
