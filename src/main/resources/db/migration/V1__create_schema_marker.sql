CREATE TABLE schema_marker (
    id SMALLINT PRIMARY KEY,
    installed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

INSERT INTO schema_marker (id, installed_at)
VALUES (1, CURRENT_TIMESTAMP);
