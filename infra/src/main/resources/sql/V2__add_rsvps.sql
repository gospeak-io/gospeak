ALTER TABLE events
    ADD COLUMN max_attendee INT;

CREATE TABLE group_members
(
    group_id     CHAR(36)  NOT NULL REFERENCES groups (id),
    user_id      CHAR(36)  NOT NULL REFERENCES users (id),
    presentation VARCHAR(4096),
    joined_at    TIMESTAMP NOT NULL,
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE event_rsvps
(
    event_id    CHAR(36)   NOT NULL REFERENCES events (id),
    user_id     CHAR(36)   NOT NULL REFERENCES users (id),
    answer      VARCHAR(4) NOT NULL, -- yes, no, wait
    answered_at TIMESTAMP  NOT NULL,
    PRIMARY KEY (event_id, user_id)
);
