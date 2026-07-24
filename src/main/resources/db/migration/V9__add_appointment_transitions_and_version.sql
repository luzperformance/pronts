ALTER TABLE appointment
DROP CONSTRAINT appointment_status_check;

ALTER TABLE appointment
ADD CONSTRAINT appointment_status_check
CHECK (status IN ('SCHEDULED', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'));

ALTER TABLE appointment
ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
