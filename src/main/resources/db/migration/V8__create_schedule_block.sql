CREATE TABLE schedule_block (
    id UUID PRIMARY KEY,
    starts_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CHECK (ends_at > starts_at)
);

CREATE INDEX schedule_block_interval_idx
    ON schedule_block (starts_at, ends_at, id);
