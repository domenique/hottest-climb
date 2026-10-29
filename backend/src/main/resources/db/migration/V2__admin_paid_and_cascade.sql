ALTER TABLE climb_entry ADD COLUMN paid BOOLEAN NOT NULL DEFAULT FALSE;

-- Deleting a climber (admin) takes their entries with it, atomically.
ALTER TABLE climb_entry DROP CONSTRAINT climb_entry_climber_id_fkey;
ALTER TABLE climb_entry
    ADD CONSTRAINT climb_entry_climber_id_fkey
    FOREIGN KEY (climber_id) REFERENCES climber (id) ON DELETE CASCADE;
