-- JSmartClinic initial schema (V001)
CREATE TABLE users (
  id INTEGER PRIMARY KEY,
  username TEXT NOT NULL UNIQUE,
  full_name TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('ADMIN','DOCTOR','NURSE','RECEPTION','BILLING')),
  specialty TEXT,
  active INTEGER NOT NULL DEFAULT 1,
  failed_logins INTEGER NOT NULL DEFAULT 0,
  locked_until TEXT,
  last_login_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE patients (
  id INTEGER PRIMARY KEY,
  mrn TEXT NOT NULL UNIQUE,
  first_name TEXT NOT NULL,
  last_name TEXT NOT NULL,
  date_of_birth TEXT NOT NULL,
  sex TEXT NOT NULL CHECK (sex IN ('MALE','FEMALE','OTHER','UNKNOWN')),
  phone TEXT,
  email TEXT,
  address TEXT,
  city TEXT,
  national_id TEXT,
  blood_type TEXT,
  marital_status TEXT,
  occupation TEXT,
  emergency_name TEXT,
  emergency_phone TEXT,
  emergency_relation TEXT,
  insurance_provider TEXT,
  insurance_number TEXT,
  photo BLOB,
  notes TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  deleted_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE INDEX idx_patients_name ON patients(last_name, first_name);

CREATE TABLE allergies (
  id INTEGER PRIMARY KEY,
  patient_id INTEGER NOT NULL REFERENCES patients(id),
  substance TEXT NOT NULL,
  reaction TEXT,
  severity TEXT CHECK (severity IN ('MILD','MODERATE','SEVERE')),
  active INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE problems (
  id INTEGER PRIMARY KEY,
  patient_id INTEGER NOT NULL REFERENCES patients(id),
  icd10_code TEXT,
  description TEXT NOT NULL,
  onset_date TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','RESOLVED')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE appointments (
  id INTEGER PRIMARY KEY,
  patient_id INTEGER NOT NULL REFERENCES patients(id),
  provider_id INTEGER NOT NULL REFERENCES users(id),
  start_at TEXT NOT NULL,
  end_at TEXT NOT NULL,
  reason TEXT,
  status TEXT NOT NULL DEFAULT 'SCHEDULED'
    CHECK (status IN ('SCHEDULED','CHECKED_IN','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW')),
  notes TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE INDEX idx_appt_provider_time ON appointments(provider_id, start_at);

CREATE TABLE encounters (
  id INTEGER PRIMARY KEY,
  patient_id INTEGER NOT NULL REFERENCES patients(id),
  provider_id INTEGER NOT NULL REFERENCES users(id),
  appointment_id INTEGER REFERENCES appointments(id),
  specialty TEXT,
  template_id TEXT,
  encounter_date TEXT NOT NULL,
  chief_complaint TEXT,
  subjective TEXT,
  objective TEXT,
  assessment TEXT,
  plan TEXT,
  template_data TEXT,
  status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SIGNED','AMENDED')),
  signed_at TEXT,
  signed_by INTEGER REFERENCES users(id),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE INDEX idx_encounter_patient ON encounters(patient_id, encounter_date);

CREATE TABLE vitals (
  id INTEGER PRIMARY KEY,
  patient_id INTEGER NOT NULL REFERENCES patients(id),
  encounter_id INTEGER REFERENCES encounters(id),
  measured_at TEXT NOT NULL,
  systolic INTEGER,
  diastolic INTEGER,
  heart_rate INTEGER,
  resp_rate INTEGER,
  temperature_c REAL,
  spo2 INTEGER,
  weight_kg REAL,
  height_cm REAL,
  glucose_mgdl REAL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE INDEX idx_vitals_patient ON vitals(patient_id, measured_at);

CREATE TABLE encounter_diagnoses (
  id INTEGER PRIMARY KEY,
  encounter_id INTEGER NOT NULL REFERENCES encounters(id),
  icd10_code TEXT,
  description TEXT NOT NULL,
  is_primary INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE medications (
  id INTEGER PRIMARY KEY,
  patient_id INTEGER NOT NULL REFERENCES patients(id),
  name TEXT NOT NULL,
  dose TEXT,
  route TEXT,
  frequency TEXT,
  start_date TEXT,
  end_date TEXT,
  active INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE prescriptions (
  id INTEGER PRIMARY KEY,
  patient_id INTEGER NOT NULL REFERENCES patients(id),
  encounter_id INTEGER REFERENCES encounters(id),
  prescriber_id INTEGER NOT NULL REFERENCES users(id),
  issued_at TEXT NOT NULL,
  notes TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE prescription_items (
  id INTEGER PRIMARY KEY,
  prescription_id INTEGER NOT NULL REFERENCES prescriptions(id) ON DELETE CASCADE,
  drug_name TEXT NOT NULL,
  strength TEXT,
  form TEXT,
  dose TEXT,
  frequency TEXT,
  duration TEXT,
  quantity TEXT,
  instructions TEXT
);

CREATE TABLE lab_tests (
  id INTEGER PRIMARY KEY,
  code TEXT UNIQUE,
  name TEXT NOT NULL,
  unit TEXT,
  ref_low REAL,
  ref_high REAL,
  category TEXT
);

CREATE TABLE lab_orders (
  id INTEGER PRIMARY KEY,
  patient_id INTEGER NOT NULL REFERENCES patients(id),
  encounter_id INTEGER REFERENCES encounters(id),
  ordered_by INTEGER NOT NULL REFERENCES users(id),
  ordered_at TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'ORDERED'
    CHECK (status IN ('ORDERED','COLLECTED','RESULTED','CANCELLED')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE lab_results (
  id INTEGER PRIMARY KEY,
  lab_order_id INTEGER NOT NULL REFERENCES lab_orders(id),
  lab_test_id INTEGER NOT NULL REFERENCES lab_tests(id),
  value_num REAL,
  value_text TEXT,
  flag TEXT CHECK (flag IN ('LOW','NORMAL','HIGH','CRITICAL')),
  resulted_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE documents (
  id INTEGER PRIMARY KEY,
  patient_id INTEGER NOT NULL REFERENCES patients(id),
  encounter_id INTEGER REFERENCES encounters(id),
  title TEXT NOT NULL,
  mime_type TEXT,
  file_name TEXT NOT NULL,
  sha256 TEXT NOT NULL,
  uploaded_by INTEGER REFERENCES users(id),
  created_at TEXT NOT NULL
);

CREATE TABLE services (
  id INTEGER PRIMARY KEY,
  code TEXT UNIQUE,
  name TEXT NOT NULL,
  price_cents INTEGER NOT NULL,
  active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE invoices (
  id INTEGER PRIMARY KEY,
  invoice_no TEXT NOT NULL UNIQUE,
  patient_id INTEGER NOT NULL REFERENCES patients(id),
  encounter_id INTEGER REFERENCES encounters(id),
  issued_at TEXT NOT NULL,
  subtotal_cents INTEGER NOT NULL,
  discount_cents INTEGER NOT NULL DEFAULT 0,
  tax_cents INTEGER NOT NULL DEFAULT 0,
  total_cents INTEGER NOT NULL,
  status TEXT NOT NULL DEFAULT 'UNPAID' CHECK (status IN ('UNPAID','PARTIAL','PAID','VOID')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE invoice_items (
  id INTEGER PRIMARY KEY,
  invoice_id INTEGER NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  service_id INTEGER REFERENCES services(id),
  description TEXT NOT NULL,
  qty INTEGER NOT NULL DEFAULT 1,
  unit_price_cents INTEGER NOT NULL
);

CREATE TABLE payments (
  id INTEGER PRIMARY KEY,
  invoice_id INTEGER NOT NULL REFERENCES invoices(id),
  amount_cents INTEGER NOT NULL,
  method TEXT NOT NULL CHECK (method IN ('CASH','CARD','INSURANCE','TRANSFER')),
  paid_at TEXT NOT NULL,
  received_by INTEGER REFERENCES users(id),
  reference TEXT
);

CREATE TABLE audit_log (
  id INTEGER PRIMARY KEY,
  at TEXT NOT NULL,
  user_id INTEGER REFERENCES users(id),
  action TEXT NOT NULL,
  entity TEXT NOT NULL,
  entity_id INTEGER,
  patient_id INTEGER,
  details TEXT
);
CREATE TRIGGER audit_no_update BEFORE UPDATE ON audit_log BEGIN SELECT RAISE(ABORT,'audit_log is append-only'); END;
CREATE TRIGGER audit_no_delete BEFORE DELETE ON audit_log BEGIN SELECT RAISE(ABORT,'audit_log is append-only'); END;

CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT);

CREATE TABLE icd10_codes (
  code TEXT PRIMARY KEY,
  description TEXT NOT NULL
);

-- Signed encounters are immutable; only the transition to AMENDED is allowed.
CREATE TRIGGER encounters_signed_immutable
BEFORE UPDATE ON encounters
WHEN OLD.status = 'SIGNED' AND NEW.status <> 'AMENDED'
BEGIN
  SELECT RAISE(ABORT, 'Signed encounters are immutable');
END;
