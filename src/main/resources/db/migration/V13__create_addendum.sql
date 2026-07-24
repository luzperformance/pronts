CREATE TABLE addendum (
    id UUID PRIMARY KEY,
    consultation_id UUID NOT NULL REFERENCES consultation (id),
    content TEXT NOT NULL CHECK (BTRIM(content) <> ''),
    justification TEXT NOT NULL CHECK (BTRIM(justification) <> ''),
    author_id UUID NOT NULL REFERENCES doctor_account (id),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX addendum_consultation_created_idx
    ON addendum (consultation_id, created_at, id);

CREATE FUNCTION reject_addendum_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'addenda are append-only';
END;
$$;

CREATE TRIGGER addendum_append_only
BEFORE UPDATE OR DELETE ON addendum
FOR EACH ROW
EXECUTE FUNCTION reject_addendum_mutation();
