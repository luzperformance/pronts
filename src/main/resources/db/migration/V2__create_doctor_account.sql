CREATE TABLE doctor_account (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    singleton_key BOOLEAN NOT NULL DEFAULT TRUE UNIQUE CHECK (singleton_key)
);
