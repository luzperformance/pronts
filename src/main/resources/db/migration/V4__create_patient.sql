CREATE TABLE patient (
    id UUID PRIMARY KEY,
    full_name TEXT NOT NULL CHECK (btrim(full_name) <> ''),
    mother_name TEXT NOT NULL CHECK (btrim(mother_name) <> ''),
    birth_date DATE NOT NULL,
    cpf VARCHAR(11) NOT NULL UNIQUE CHECK (cpf ~ '^[0-9]{11}$'),
    phone TEXT NOT NULL CHECK (phone ~ '^[0-9]+$'),
    email TEXT,
    address TEXT,
    emergency_contact TEXT,
    insurance TEXT,
    allergies TEXT,
    notes TEXT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    version BIGINT NOT NULL
);
