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
