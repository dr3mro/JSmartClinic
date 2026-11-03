-- Clinical rows hanging off a signed encounter are immutable too.
CREATE TRIGGER encounter_diagnoses_signed_insert
BEFORE INSERT ON encounter_diagnoses
WHEN NEW.encounter_id IS NOT NULL
 AND (SELECT signed_at FROM encounters WHERE id = NEW.encounter_id) IS NOT NULL
BEGIN
  SELECT RAISE(ABORT, 'Signed encounter clinical data is immutable');
END;

CREATE TRIGGER encounter_diagnoses_signed_update
BEFORE UPDATE ON encounter_diagnoses
WHEN (SELECT signed_at FROM encounters WHERE id = OLD.encounter_id) IS NOT NULL
 OR (SELECT signed_at FROM encounters WHERE id = NEW.encounter_id) IS NOT NULL
BEGIN
  SELECT RAISE(ABORT, 'Signed encounter clinical data is immutable');
END;

CREATE TRIGGER encounter_diagnoses_signed_delete
BEFORE DELETE ON encounter_diagnoses
WHEN (SELECT signed_at FROM encounters WHERE id = OLD.encounter_id) IS NOT NULL
BEGIN
  SELECT RAISE(ABORT, 'Signed encounter clinical data is immutable');
END;

CREATE TRIGGER vitals_signed_insert
BEFORE INSERT ON vitals
WHEN NEW.encounter_id IS NOT NULL
 AND (SELECT signed_at FROM encounters WHERE id = NEW.encounter_id) IS NOT NULL
BEGIN
  SELECT RAISE(ABORT, 'Signed encounter clinical data is immutable');
END;

CREATE TRIGGER vitals_signed_update
BEFORE UPDATE ON vitals
WHEN (OLD.encounter_id IS NOT NULL
      AND (SELECT signed_at FROM encounters WHERE id = OLD.encounter_id) IS NOT NULL)
 OR (NEW.encounter_id IS NOT NULL
      AND (SELECT signed_at FROM encounters WHERE id = NEW.encounter_id) IS NOT NULL)
BEGIN
  SELECT RAISE(ABORT, 'Signed encounter clinical data is immutable');
END;

CREATE TRIGGER vitals_signed_delete
BEFORE DELETE ON vitals
WHEN OLD.encounter_id IS NOT NULL
 AND (SELECT signed_at FROM encounters WHERE id = OLD.encounter_id) IS NOT NULL
BEGIN
  SELECT RAISE(ABORT, 'Signed encounter clinical data is immutable');
END;
