ALTER TABLE consultation
DROP CONSTRAINT consultation_status_check;

ALTER TABLE consultation
ADD COLUMN finalized_by UUID REFERENCES doctor_account (id),
ADD COLUMN finalized_at TIMESTAMP(6) WITH TIME ZONE,
ADD CONSTRAINT consultation_status_check
    CHECK (status IN ('DRAFT', 'FINALIZED')),
ADD CONSTRAINT consultation_finalization_metadata_check
    CHECK (
        (status = 'DRAFT' AND finalized_by IS NULL AND finalized_at IS NULL)
        OR
        (status = 'FINALIZED' AND finalized_by IS NOT NULL AND finalized_at IS NOT NULL)
    );
