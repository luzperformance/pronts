CREATE TABLE consultation (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES patient (id),
    appointment_id UUID UNIQUE REFERENCES appointment (id),
    anamnesis TEXT,
    chief_complaint TEXT,
    physical_examination TEXT,
    diagnostic_hypotheses TEXT,
    treatment_plan TEXT,
    observations TEXT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT')),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);

CREATE INDEX consultation_patient_created_idx
    ON consultation (patient_id, created_at, id);
