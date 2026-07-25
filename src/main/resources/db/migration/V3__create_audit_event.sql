CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES doctor_account (id),
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id UUID,
    outcome VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    correlation_id VARCHAR(36) NOT NULL
);

CREATE INDEX audit_event_occurred_at_idx
    ON audit_event (occurred_at DESC, id DESC);

CREATE INDEX audit_event_action_occurred_at_idx
    ON audit_event (action, occurred_at DESC, id DESC);
