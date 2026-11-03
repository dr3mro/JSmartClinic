-- Signed clinical content is immutable. Only the status transition SIGNED -> AMENDED is allowed.
DROP TRIGGER IF EXISTS encounters_signed_immutable;

CREATE TRIGGER encounters_signed_immutable
BEFORE UPDATE ON encounters
WHEN OLD.signed_at IS NOT NULL
 AND (
   NEW.chief_complaint IS NOT OLD.chief_complaint
   OR NEW.subjective IS NOT OLD.subjective
   OR NEW.objective IS NOT OLD.objective
   OR NEW.assessment IS NOT OLD.assessment
   OR NEW.plan IS NOT OLD.plan
   OR NEW.template_data IS NOT OLD.template_data
   OR NEW.patient_id IS NOT OLD.patient_id
   OR NEW.provider_id IS NOT OLD.provider_id
   OR NEW.encounter_date IS NOT OLD.encounter_date
   OR NEW.signed_at IS NOT OLD.signed_at
   OR NEW.signed_by IS NOT OLD.signed_by
   OR (NEW.status IS NOT OLD.status AND NOT (OLD.status = 'SIGNED' AND NEW.status = 'AMENDED'))
 )
BEGIN
  SELECT RAISE(ABORT, 'Signed encounters are immutable');
END;

CREATE TRIGGER encounters_signed_no_delete
BEFORE DELETE ON encounters
WHEN OLD.signed_at IS NOT NULL
BEGIN
  SELECT RAISE(ABORT, 'Signed encounters cannot be deleted');
END;
