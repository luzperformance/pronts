ALTER TABLE audit_event
ADD COLUMN changed_fields JSONB NOT NULL DEFAULT '[]'::jsonb
CHECK (jsonb_typeof(changed_fields) = 'array');
