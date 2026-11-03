-- Critical lab flags need explicit limits; a reference range alone cannot define them.
ALTER TABLE lab_tests ADD COLUMN crit_low REAL;
ALTER TABLE lab_tests ADD COLUMN crit_high REAL;

-- Potassium: below 2.5 or above 6.5 mmol/L is a well-recognised critical value.
UPDATE lab_tests SET crit_low = 2.5, crit_high = 6.5 WHERE code = 'K';

-- Sodium: below 120 or above 160 mmol/L is a well-recognised critical value.
UPDATE lab_tests SET crit_low = 120, crit_high = 160 WHERE code = 'NA';

-- Fasting glucose: below 40 or above 450 mg/dL is a well-recognised critical value.
UPDATE lab_tests SET crit_low = 40, crit_high = 450 WHERE code = 'GLU';
