CREATE TABLE appointment (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES patient (id),
    starts_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    duration_minutes INTEGER NOT NULL
        CHECK (duration_minutes IN (15, 30, 45, 60)),
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('SCHEDULED')),
    CHECK (ends_at = starts_at + duration_minutes * INTERVAL '1 minute')
);

CREATE INDEX appointment_interval_idx
    ON appointment (starts_at, ends_at, id);

CREATE INDEX appointment_patient_interval_idx
    ON appointment (patient_id, starts_at, id);
