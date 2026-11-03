-- Seed catalogues and default settings (V002). No default user is created.
INSERT INTO lab_tests (code, name, unit, ref_low, ref_high, category) VALUES
  ('HGB', 'Haemoglobin', 'g/dL', 12.0, 17.5, 'Haematology'),
  ('WBC', 'White blood cells', '10^9/L', 4.0, 11.0, 'Haematology'),
  ('PLT', 'Platelets', '10^9/L', 150, 400, 'Haematology'),
  ('GLU', 'Fasting glucose', 'mg/dL', 70, 99, 'Chemistry'),
  ('HBA1C', 'HbA1c', '%', 4.0, 5.6, 'Chemistry'),
  ('NA', 'Sodium', 'mmol/L', 135, 145, 'Chemistry'),
  ('K', 'Potassium', 'mmol/L', 3.5, 5.1, 'Chemistry'),
  ('CREA', 'Creatinine', 'mg/dL', 0.6, 1.3, 'Renal'),
  ('UREA', 'Urea', 'mg/dL', 7, 20, 'Renal'),
  ('ALT', 'ALT', 'U/L', 7, 56, 'Liver'),
  ('AST', 'AST', 'U/L', 10, 40, 'Liver'),
  ('TSH', 'TSH', 'mIU/L', 0.4, 4.0, 'Endocrine'),
  ('CRP', 'C-reactive protein', 'mg/L', 0, 5, 'Inflammation'),
  ('CHOL', 'Total cholesterol', 'mg/dL', 0, 200, 'Lipids'),
  ('LDL', 'LDL cholesterol', 'mg/dL', 0, 100, 'Lipids'),
  ('HDL', 'HDL cholesterol', 'mg/dL', 40, 90, 'Lipids'),
  ('TRIG', 'Triglycerides', 'mg/dL', 0, 150, 'Lipids');

INSERT INTO services (code, name, price_cents, active) VALUES
  ('CONS', 'General consultation', 5000, 1),
  ('FOLLOW', 'Follow-up consultation', 3500, 1),
  ('NEWP', 'New patient consultation', 7500, 1),
  ('ECG', 'Electrocardiogram (ECG)', 4000, 1),
  ('VACC', 'Vaccination', 2500, 1),
  ('WOUND', 'Wound dressing', 3000, 1),
  ('LAB', 'Laboratory panel', 6000, 1),
  ('XRAY', 'X-ray', 8000, 1);

INSERT INTO settings (key, value) VALUES
  ('clinic_name', 'JSmartClinic'),
  ('clinic_address', '1 Health Street'),
  ('clinic_phone', '+1 555 0100'),
  ('clinic_email', 'front-desk@jsmartclinic.local'),
  ('currency', '$'),
  ('tax_rate', '0'),
  ('theme', 'light'),
  ('language', 'en'),
  ('auto_backup', 'true'),
  ('last_backup_at', '');
