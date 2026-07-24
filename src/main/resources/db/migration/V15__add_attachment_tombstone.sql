ALTER TABLE attachment
DROP CONSTRAINT attachment_status_check;

ALTER TABLE attachment
ADD COLUMN removed_by UUID REFERENCES doctor_account (id),
ADD COLUMN removal_justification TEXT,
ADD COLUMN removed_at TIMESTAMP(6) WITH TIME ZONE,
ADD COLUMN binary_cleanup_pending BOOLEAN NOT NULL DEFAULT FALSE,
ADD CONSTRAINT attachment_status_check
    CHECK (status IN ('ACTIVE', 'REMOVED')),
ADD CONSTRAINT attachment_tombstone_check
    CHECK (
        (
            status = 'ACTIVE'
            AND removed_by IS NULL
            AND removal_justification IS NULL
            AND removed_at IS NULL
            AND binary_cleanup_pending = FALSE
        )
        OR
        (
            status = 'REMOVED'
            AND removed_by IS NOT NULL
            AND removal_justification IS NOT NULL
            AND BTRIM(removal_justification) <> ''
            AND removed_at IS NOT NULL
        )
    );
