-- A clinician administrator keeps the admin role but gains the doctor's clinical permissions.
ALTER TABLE users ADD COLUMN clinician INTEGER NOT NULL DEFAULT 0 CHECK (clinician IN (0, 1));
