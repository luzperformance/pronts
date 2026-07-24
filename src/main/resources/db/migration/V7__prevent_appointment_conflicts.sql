CREATE TABLE schedule_calendar (
    id SMALLINT PRIMARY KEY CHECK (id = 1)
);

INSERT INTO schedule_calendar (id)
VALUES (1);

ALTER TABLE appointment
DROP CONSTRAINT appointment_status_check;

ALTER TABLE appointment
ADD CONSTRAINT appointment_status_check
CHECK (status IN ('SCHEDULED', 'CANCELLED'));
