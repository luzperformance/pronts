CREATE TABLE attachment (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES patient (id),
    consultation_id UUID REFERENCES consultation (id),
    original_filename TEXT NOT NULL,
    media_type VARCHAR(80) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes BETWEEN 1 AND 10485760),
    sha256 VARCHAR(64) NOT NULL,
    storage_key VARCHAR(36) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE')),
    uploaded_by UUID NOT NULL REFERENCES doctor_account (id),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX attachment_patient_status_created_idx
    ON attachment (patient_id, status, created_at, id);
