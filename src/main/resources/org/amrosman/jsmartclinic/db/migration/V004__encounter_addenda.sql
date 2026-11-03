-- Addenda are linked to the signed note they amend.
ALTER TABLE encounters ADD COLUMN amends_encounter_id INTEGER REFERENCES encounters(id);
