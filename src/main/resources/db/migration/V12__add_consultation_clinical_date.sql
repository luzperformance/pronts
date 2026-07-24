ALTER TABLE consultation
ADD COLUMN clinical_date TIMESTAMP(6) WITH TIME ZONE;

UPDATE consultation
SET clinical_date = created_at;

ALTER TABLE consultation
ALTER COLUMN clinical_date SET NOT NULL;

DROP INDEX consultation_patient_created_idx;

CREATE INDEX consultation_medical_record_idx
    ON consultation (patient_id, status, clinical_date, created_at, id);
