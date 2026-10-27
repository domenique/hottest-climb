CREATE TABLE climber (
    id       UUID PRIMARY KEY,
    name     VARCHAR(30) NOT NULL,
    pin_hash VARCHAR(100) NOT NULL
);

CREATE UNIQUE INDEX climber_name_lower_uq ON climber (lower(name));

CREATE TABLE climb_entry (
    id         UUID PRIMARY KEY,
    climber_id UUID NOT NULL REFERENCES climber (id),
    meters     INTEGER NOT NULL CHECK (meters > 0),
    logged_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX climb_entry_climber_idx ON climb_entry (climber_id);
