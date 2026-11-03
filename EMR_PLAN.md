# JSmartClinic — Full EMR Development Plan (for opencode)

> **Agent instructions:** Work through the phases **in order**. Finish each phase completely (code + tests + `./mvnw -q verify` passing) before starting the next. After each phase, tick its checkboxes in this file and append a short entry to the **Progress Log** at the bottom. Never delete existing functionality or styling without replacing it.

---

## 1. Project Context

| Item | Value |
|---|---|
| App | JSmartClinic — multi-specialty desktop EMR (Electronic Medical Record) |
| UI | JavaFX **25.0.1** + FXML + CSS (`app.css`), Scene Builder–compatible |
| Language level | Java **21** (`maven.compiler` `<release>21</release>`); requires JDK **23+** at build/run time because JavaFX 25 targets Java 23 (verified on JDK 27) |
| Build | Maven (`./mvnw`), JPMS module `org.amrosman.jsmartclinic` |
| Database | **SQLite** via `org.xerial:sqlite-jdbc` (single local file) |
| Packaging | `jpackage` → `.dmg` (macOS) and `.exe` (Windows) |
| Existing UI | `hello-view.fxml` dashboard (sidebar, topbar, stat cards, patient table, summary panel, schedule) + `app.css` theme. **Reuse this look everywhere.** |

### Hard constraints
- No Spring / no web server. Plain Java + JavaFX + JDBC.
- Keep the module name and main class (`HelloApplication`, launched via `Launcher`) working; renaming is allowed in Phase 0 only if all references are updated.
- Every new dependency must be added to **both** `pom.xml` and `module-info.java` (`requires …`), and must work on the module path.
- All FXML must open cleanly in Scene Builder (no custom controls without a no-arg constructor).
- All DB access goes through repositories using `PreparedStatement` — **never** string-concatenated SQL.
- UI thread must never block: DB work runs in `javafx.concurrent.Task` / an `ExecutorService`, results applied with `Platform.runLater`.

---

## 2. Target Architecture

```
org.amrosman.jsmartclinic
├── HelloApplication / Launcher        # entry point → loads LoginView, then MainView
├── app/        AppContext (manual DI: holds services/repos), Session (current user), Navigator
├── domain/     records & enums: Patient, Appointment, Encounter, Vital, Diagnosis, Allergy,
│               Medication, Prescription, LabOrder, LabResult, Invoice, Payment, User, Role, AuditEvent
├── db/         Database (connection factory, PRAGMAs), MigrationRunner, BackupService
├── repository/ PatientRepository, AppointmentRepository, … (JDBC, one per aggregate)
├── service/    PatientService, SchedulingService, EncounterService, BillingService,
│               AuthService, AuditService, ReportService, PdfService, TemplateService
├── ui/
│   ├── MainController (shell: sidebar + topbar + content StackPane)
│   ├── dashboard/  patients/  appointments/  encounters/  prescriptions/
│   ├── labs/  billing/  reports/  settings/  auth/
│   └── components/ (reusable: StatusBadge, AvatarView, FormField helpers, Toast)
└── util/       Validators, DateUtil, Ids, PasswordHasher, Csv
resources/org/amrosman/jsmartclinic/
├── app.css, views/*.fxml, db/migration/V001__init.sql, V002__…sql, templates/*.json, i18n/messages*.properties
```

**Navigation:** `MainView.fxml` = current dashboard shell. The center area becomes a `StackPane contentArea`; sidebar toggles call `Navigator.show("patients")`, which loads (and caches) the matching FXML into `contentArea`. The current dashboard content moves to `views/dashboard.fxml`.

**Data location:** DB file stored in the per-user app data folder (never inside the app bundle):
- macOS: `~/Library/Application Support/JSmartClinic/emr.db`
- Windows: `%APPDATA%\JSmartClinic\emr.db`
- Linux: `~/.local/share/JSmartClinic/emr.db`

**Connection setup (run on every connection):**
```sql
PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;
PRAGMA busy_timeout = 5000;
```

---

## 3. Database Schema (initial — `V001__init.sql`)

Use `TEXT` ISO-8601 for dates/times, `INTEGER` for money in **cents**, soft delete via `deleted_at`. Every table has `created_at`, `updated_at`.

```sql
CREATE TABLE schema_version (version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL);

CREATE TABLE users (
  id INTEGER PRIMARY KEY, username TEXT NOT NULL UNIQUE, full_name TEXT NOT NULL,
  password_hash TEXT NOT NULL, role TEXT NOT NULL CHECK (role IN ('ADMIN','DOCTOR','NURSE','RECEPTION','BILLING')),
  specialty TEXT, active INTEGER NOT NULL DEFAULT 1, failed_logins INTEGER NOT NULL DEFAULT 0,
  locked_until TEXT, last_login_at TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL);

CREATE TABLE patients (
  id INTEGER PRIMARY KEY, mrn TEXT NOT NULL UNIQUE,            -- e.g. P-000123
  first_name TEXT NOT NULL, last_name TEXT NOT NULL, date_of_birth TEXT NOT NULL,
  sex TEXT NOT NULL CHECK (sex IN ('MALE','FEMALE','OTHER','UNKNOWN')),
  phone TEXT, email TEXT, address TEXT, city TEXT, national_id TEXT,
  blood_type TEXT, marital_status TEXT, occupation TEXT,
  emergency_name TEXT, emergency_phone TEXT, emergency_relation TEXT,
  insurance_provider TEXT, insurance_number TEXT, photo BLOB, notes TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE', deleted_at TEXT,
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL);
CREATE INDEX idx_patients_name ON patients(last_name, first_name);

CREATE TABLE allergies (
  id INTEGER PRIMARY KEY, patient_id INTEGER NOT NULL REFERENCES patients(id),
  substance TEXT NOT NULL, reaction TEXT, severity TEXT CHECK (severity IN ('MILD','MODERATE','SEVERE')),
  active INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL, updated_at TEXT NOT NULL);

CREATE TABLE problems (                                          -- problem list / chronic conditions
  id INTEGER PRIMARY KEY, patient_id INTEGER NOT NULL REFERENCES patients(id),
  icd10_code TEXT, description TEXT NOT NULL, onset_date TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','RESOLVED')),
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL);

CREATE TABLE appointments (
  id INTEGER PRIMARY KEY, patient_id INTEGER NOT NULL REFERENCES patients(id),
  provider_id INTEGER NOT NULL REFERENCES users(id),
  start_at TEXT NOT NULL, end_at TEXT NOT NULL, reason TEXT,
  status TEXT NOT NULL DEFAULT 'SCHEDULED'
    CHECK (status IN ('SCHEDULED','CHECKED_IN','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW')),
  notes TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL);
CREATE INDEX idx_appt_provider_time ON appointments(provider_id, start_at);

CREATE TABLE encounters (                                        -- a visit
  id INTEGER PRIMARY KEY, patient_id INTEGER NOT NULL REFERENCES patients(id),
  provider_id INTEGER NOT NULL REFERENCES users(id), appointment_id INTEGER REFERENCES appointments(id),
  specialty TEXT, template_id TEXT, encounter_date TEXT NOT NULL,
  chief_complaint TEXT, subjective TEXT, objective TEXT, assessment TEXT, plan TEXT,
  template_data TEXT,                                            -- JSON from specialty template
  status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SIGNED','AMENDED')),
  signed_at TEXT, signed_by INTEGER REFERENCES users(id),
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL);

CREATE TABLE vitals (
  id INTEGER PRIMARY KEY, patient_id INTEGER NOT NULL REFERENCES patients(id),
  encounter_id INTEGER REFERENCES encounters(id), measured_at TEXT NOT NULL,
  systolic INTEGER, diastolic INTEGER, heart_rate INTEGER, resp_rate INTEGER,
  temperature_c REAL, spo2 INTEGER, weight_kg REAL, height_cm REAL, glucose_mgdl REAL,
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL);

CREATE TABLE encounter_diagnoses (
  id INTEGER PRIMARY KEY, encounter_id INTEGER NOT NULL REFERENCES encounters(id),
  icd10_code TEXT, description TEXT NOT NULL, is_primary INTEGER NOT NULL DEFAULT 0);

CREATE TABLE medications (                                       -- current/home medication list
  id INTEGER PRIMARY KEY, patient_id INTEGER NOT NULL REFERENCES patients(id),
  name TEXT NOT NULL, dose TEXT, route TEXT, frequency TEXT,
  start_date TEXT, end_date TEXT, active INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL);

CREATE TABLE prescriptions (
  id INTEGER PRIMARY KEY, patient_id INTEGER NOT NULL REFERENCES patients(id),
  encounter_id INTEGER REFERENCES encounters(id), prescriber_id INTEGER NOT NULL REFERENCES users(id),
  issued_at TEXT NOT NULL, notes TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL);
CREATE TABLE prescription_items (
  id INTEGER PRIMARY KEY, prescription_id INTEGER NOT NULL REFERENCES prescriptions(id) ON DELETE CASCADE,
  drug_name TEXT NOT NULL, strength TEXT, form TEXT, dose TEXT, frequency TEXT,
  duration TEXT, quantity TEXT, instructions TEXT);

CREATE TABLE lab_tests (                                         -- catalog
  id INTEGER PRIMARY KEY, code TEXT UNIQUE, name TEXT NOT NULL, unit TEXT,
  ref_low REAL, ref_high REAL, category TEXT);
CREATE TABLE lab_orders (
  id INTEGER PRIMARY KEY, patient_id INTEGER NOT NULL REFERENCES patients(id),
  encounter_id INTEGER REFERENCES encounters(id), ordered_by INTEGER NOT NULL REFERENCES users(id),
  ordered_at TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'ORDERED'
    CHECK (status IN ('ORDERED','COLLECTED','RESULTED','CANCELLED')),
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL);
CREATE TABLE lab_results (
  id INTEGER PRIMARY KEY, lab_order_id INTEGER NOT NULL REFERENCES lab_orders(id),
  lab_test_id INTEGER NOT NULL REFERENCES lab_tests(id),
  value_num REAL, value_text TEXT, flag TEXT CHECK (flag IN ('LOW','NORMAL','HIGH','CRITICAL')),
  resulted_at TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL);

CREATE TABLE documents (
  id INTEGER PRIMARY KEY, patient_id INTEGER NOT NULL REFERENCES patients(id),
  encounter_id INTEGER REFERENCES encounters(id), title TEXT NOT NULL, mime_type TEXT,
  file_name TEXT NOT NULL, sha256 TEXT NOT NULL,                 -- file stored in app-data/documents/<sha256>
  uploaded_by INTEGER REFERENCES users(id), created_at TEXT NOT NULL);

CREATE TABLE services (                                          -- billing catalog
  id INTEGER PRIMARY KEY, code TEXT UNIQUE, name TEXT NOT NULL,
  price_cents INTEGER NOT NULL, active INTEGER NOT NULL DEFAULT 1);
CREATE TABLE invoices (
  id INTEGER PRIMARY KEY, invoice_no TEXT NOT NULL UNIQUE, patient_id INTEGER NOT NULL REFERENCES patients(id),
  encounter_id INTEGER REFERENCES encounters(id), issued_at TEXT NOT NULL,
  subtotal_cents INTEGER NOT NULL, discount_cents INTEGER NOT NULL DEFAULT 0,
  tax_cents INTEGER NOT NULL DEFAULT 0, total_cents INTEGER NOT NULL,
  status TEXT NOT NULL DEFAULT 'UNPAID' CHECK (status IN ('UNPAID','PARTIAL','PAID','VOID')),
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL);
CREATE TABLE invoice_items (
  id INTEGER PRIMARY KEY, invoice_id INTEGER NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  service_id INTEGER REFERENCES services(id), description TEXT NOT NULL,
  qty INTEGER NOT NULL DEFAULT 1, unit_price_cents INTEGER NOT NULL);
CREATE TABLE payments (
  id INTEGER PRIMARY KEY, invoice_id INTEGER NOT NULL REFERENCES invoices(id),
  amount_cents INTEGER NOT NULL, method TEXT NOT NULL CHECK (method IN ('CASH','CARD','INSURANCE','TRANSFER')),
  paid_at TEXT NOT NULL, received_by INTEGER REFERENCES users(id), reference TEXT);

CREATE TABLE audit_log (                                         -- append-only
  id INTEGER PRIMARY KEY, at TEXT NOT NULL, user_id INTEGER REFERENCES users(id),
  action TEXT NOT NULL,                                          -- LOGIN, VIEW, CREATE, UPDATE, DELETE, PRINT, EXPORT
  entity TEXT NOT NULL, entity_id INTEGER, patient_id INTEGER, details TEXT);
CREATE TRIGGER audit_no_update BEFORE UPDATE ON audit_log BEGIN SELECT RAISE(ABORT,'audit_log is append-only'); END;
CREATE TRIGGER audit_no_delete BEFORE DELETE ON audit_log BEGIN SELECT RAISE(ABORT,'audit_log is append-only'); END;

CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT);        -- clinic name, address, logo path, currency…
```

Seed data goes in a separate migration (`V002__seed.sql`): default lab test catalog, sample service catalog, and **no** default user (first-run wizard creates the admin).

---

## 4. Phases & Tasks

### Phase 0 — Foundation & cleanup
- [x] Remove unused heavy deps (`fxgl`, and any of `tilesfx`/`formsfx`/`bootstrapfx` that end up unused); keep `controlsfx`, `ikonli-javafx` (+ add `ikonli-fontawesome6-pack` and replace unicode icons with `FontIcon`).
- [x] Add deps: `org.xerial:sqlite-jdbc`, `org.slf4j:slf4j-api` + `slf4j-simple`, `org.apache.pdfbox:pdfbox` (3.x), test: `junit-jupiter`, `org.testfx:testfx-junit5` (optional), `org.assertj:assertj-core`.
- [x] Update `module-info.java` (`requires java.sql; requires org.xerial.sqlitejdbc; requires org.slf4j; requires org.apache.pdfbox; opens … to javafx.fxml` for every ui sub-package).
- [x] Create package structure from §2; add `AppContext`, `Navigator`, `Session`.
- [x] Split current `hello-view.fxml` into `main-view.fxml` (shell) + `views/dashboard.fxml`; sidebar navigation loads views into `contentArea`.
- [x] `./mvnw -q verify` passes; app launches with `./mvnw javafx:run`.

### Phase 1 — Database layer
- [x] `Database` creates app-data folder, opens connection, applies PRAGMAs.
- [x] `MigrationRunner` executes `db/migration/V###__*.sql` in order inside a transaction, records in `schema_version`; idempotent on restart.
- [x] `V001__init.sql` (schema above) and `V002__seed.sql`.
- [x] Base repository helpers (row mappers, `Instant`/`LocalDate` ↔ ISO text, transactions).
- [x] Unit tests run against a temp-file SQLite DB.

### Phase 2 — Authentication, users & audit
- [x] First-run wizard: if `users` is empty, show "Create administrator" screen (clinic name + admin account).
- [x] `LoginView.fxml` styled like the app; `AuthService` with **PBKDF2WithHmacSHA256** (≥ 210k iterations, 16-byte random salt, stored as `pbkdf2$iter$salt$hash`), constant-time compare.
- [x] Account lockout after 5 failed attempts (15 min); password policy (≥ 10 chars).
- [x] Roles & permissions matrix (§5) enforced in **services** (not only by hiding buttons).
- [x] Auto-lock after 10 min inactivity → re-enter password.
- [x] `AuditService` logs LOGIN/LOGOUT, every patient chart VIEW, CREATE/UPDATE/DELETE, PRINT/EXPORT.
- [x] Settings → Users screen (Admin): create, edit role, deactivate, reset password.

### Phase 3 — Patient registry
- [x] Patients list view: searchable/sortable/paginated table (name, MRN, phone, DOB), status badges (reuse CSS).
- [x] Add/Edit Patient dialog with validation (required fields, DOB not in future, phone/email format), auto-generated MRN.
- [x] Patient photo (choose file → resized, stored as BLOB).
- [x] Soft delete / archive; duplicate detection warning (same name + DOB).
- [x] **Patient Chart** view (tabbed): Summary · Encounters · Problems · Allergies · Medications · Vitals · Labs · Prescriptions · Documents · Billing. Header shows avatar, name, age, sex, MRN, allergy banner (red).

### Phase 4 — Appointments & scheduling
- [x] Day and week calendar view per provider (custom `GridPane`-based or CalendarFX if module-compatible).
- [x] Create/edit/cancel/reschedule; conflict detection for the same provider; status workflow (Scheduled → Checked-in → In progress → Completed / No-show / Cancelled).
- [x] Waiting room list (checked-in patients with wait time).
- [x] Dashboard "Today's Schedule" & stat cards read from DB.

### Phase 5 — Clinical encounters
- [x] Start encounter from appointment or chart; SOAP note editor (chief complaint, S, O, A, P).
- [x] Vitals entry with auto BMI and range highlighting.
- [x] Diagnoses with ICD-10 search (bundle a CSV subset of ICD-10-CM in resources, loaded into a lookup table).
- [x] Problem list, allergy list, medication list CRUD from the chart.
- [x] **Sign** encounter → becomes read-only; later changes create an **addendum** (`AMENDED`), original preserved.
- [x] Autosave drafts every 30 s.

### Phase 6 — Multi-specialty templates
- [x] JSON template format in `resources/templates/` (`general.json`, `cardiology.json`, `pediatrics.json`, `obgyn.json`, `dermatology.json`, `ophthalmology.json`): sections → fields (`text`, `textarea`, `number`, `select`, `checkbox`, `date`, with units/ranges).
- [x] `TemplateService` renders a template into JavaFX controls dynamically; values saved as JSON in `encounters.template_data`.
- [x] Provider's specialty selects the default template; user can switch.
- [x] Use a JSON library that works on the module path (e.g. Jackson 2.17+ — add `requires com.fasterxml.jackson.databind` and `opens domain to com.fasterxml.jackson.databind`).

### Phase 7 — Prescriptions
- [x] Prescription builder (multiple items: drug, strength, form, dose, frequency, duration, qty, instructions).
- [x] Local drug list (CSV of common generics) with autocomplete.
- [x] Allergy cross-check warning (substring match drug ↔ allergy substance) — clearly labelled as basic.
- [x] Print / export Rx as PDF with clinic header, prescriber name, patient details, date.
- [x] "Renew" from a previous prescription.

### Phase 8 — Labs & documents
- [x] Lab test catalog management (Settings).
- [x] Order labs from an encounter; enter results; auto-flag LOW/HIGH/CRITICAL from reference ranges.
- [x] Result trend chart per test (`LineChart`).
- [x] Documents tab: attach PDF/images (copied to app-data `documents/` named by SHA-256), preview images, open PDFs with system viewer.

### Phase 9 — Billing
- [x] Services catalog CRUD.
- [x] Create invoice from encounter (prefill services), discounts, tax rate from settings.
- [x] Record payments (partial/full), status auto-updates, receipt PDF.
- [x] Patient balance shown on chart; outstanding invoices list.

### Phase 10 — Dashboard & reports
- [x] Live stat cards (today's appointments, waiting, new patients this week, pending labs, today's revenue).
- [x] Reports: appointments by provider/date range, revenue by period, top diagnoses, patient demographics; export CSV & PDF; charts with JavaFX `BarChart`/`PieChart`.

### Phase 11 — Settings, backup & polish
- [x] Clinic profile (name, address, phone, logo) used in all PDFs.
- [x] Backup: SQLite online backup API (`backup to <file>`) on demand + automatic daily backup (keep last 14); Restore with confirmation.
- [x] Global search (topbar) across patients (name/MRN/phone).
- [x] Keyboard shortcuts (⌘/Ctrl+N new patient, ⌘/Ctrl+F search, ⌘/Ctrl+S save).
- [x] Toast notifications for save/success/error; consistent empty states and loading spinners.
- [x] Light/dark theme toggle (`app.css` + `app-dark.css`).
- [x] i18n with `ResourceBundle` (English + Arabic, RTL via `NodeOrientation.RIGHT_TO_LEFT`).

### Phase 12 — Packaging & release
- [x] Maven profile `package-mac` / `package-win` running `jpackage` (via `org.panteleyev:jpackage-maven-plugin`) with app icon, version, vendor.
- [x] jlink runtime limited to required modules; `--java-options "-XX:+DisableAttachMechanism"`.
- [x] GitHub Actions matrix (`macos-latest`, `windows-latest`) building DMG and EXE as artifacts.
- [x] README with build/run/package instructions.

---

## 5. Roles & Permissions

| Capability | ADMIN | DOCTOR | NURSE | RECEPTION | BILLING |
|---|:-:|:-:|:-:|:-:|:-:|
| Manage users & settings | ✅ | | | | |
| Register / edit patient demographics | ✅ | ✅ | ✅ | ✅ | |
| View clinical chart | ✅ | ✅ | ✅ | | |
| Schedule appointments | ✅ | ✅ | ✅ | ✅ | |
| Record vitals | ✅ | ✅ | ✅ | | |
| Write & sign encounters, diagnoses | | ✅ | | | |
| Prescribe | | ✅ | | | |
| Order labs / enter results | | ✅ | ✅ | | |
| Invoices & payments | ✅ | | | ✅ | ✅ |
| Reports | ✅ | ✅ | | | ✅ |
| Backup / restore | ✅ | | | | |
| View audit log | ✅ | | | | |

---

## 6. Security & Privacy Requirements
- Parameterized SQL only; validate all input at the service layer.
- Passwords: PBKDF2 (see Phase 2); never log passwords, patient names, or clinical text.
- Permission checks in services throw `AccessDeniedException`; UI hides what the role can't do.
- Audit every chart view and data change; audit table append-only (triggers).
- Inactivity auto-lock; logout clears `Session` and cached views.
- DB file and documents folder created with user-only permissions (POSIX `rw-------` where supported).
- Optional (later): at-rest encryption via SQLCipher-compatible driver; document trade-offs in README.
- Signed encounters are immutable (enforced in service + DB trigger blocking UPDATE when `status='SIGNED'` except to `AMENDED`).

---

## 7. Coding Conventions
- Domain objects are Java `record`s; enums for statuses.
- One FXML + one controller per view; controllers contain **no SQL** and minimal logic (call services).
- Long operations use `Task<T>`; show a `ProgressIndicator` overlay while loading.
- All styles in CSS — no inline `style=""` in FXML except prototyping.
- Reuse CSS classes: `.card`, `.card-title`, `.primary-button`, `.badge-*`, `.section-label`, `.muted`, `.vital-tile`. Add `.secondary-button`, `.danger-button`, `.form-label`, `.form-field`, `.dialog-pane` as needed.
- Logging via SLF4J; user-facing errors via a common `ErrorDialog`.
- Tests: services & repositories ≥ 70 % line coverage (JaCoCo); use temp SQLite DBs, never the real one.

---

## 8. Definition of Done (each phase)
1. Features in the phase checklist work end-to-end in the running app.
2. `./mvnw -q verify` passes (compile + tests).
3. No new warnings in the JavaFX console about missing modules/CSS.
4. New FXML opens in Scene Builder.
5. Checkboxes ticked and Progress Log updated.

---

## 9. Progress Log
| Date | Phase | Summary |
|---|---|---|
| 2026-11-03 | Phase 0 — Foundation | Removed unused heavy UI dependencies; added SQLite, SLF4J, PDFBox 3, Jackson, Ikonli FontAwesome 6, JUnit 5 and AssertJ to both `pom.xml` and `module-info.java`; added `AppContext`, `Session`, `Navigator`; split the original dashboard into `main-view.fxml` + `dashboard.fxml` with container navigation. `./mvnw verify` and `javafx:run` pass. |
| 2026-11-03 | Phase 1 — Database layer | App-data database creation with permission hardening and PRAGMAs; transactional, idempotent `MigrationRunner`; `V001__init.sql` schema/triggers and `V002__seed.sql`; JDBC repositories with prepared statements, row mappers, ISO date helpers and transactions; repository tests use temporary databases. |
| 2026-11-03 | Phase 2 — Auth, users and audit | First-run administrator wizard; PBKDF2-HMAC-SHA256 (210k iterations, 16-byte salt, constant-time compare); 5-failure/15-minute lockout and 10-character password policy; service-layer permission matrix; 10-minute inactivity lock; login/logout/chart-view/data-change audit; admin user management screen. |
| 2026-11-03 | Phase 3 — Patient registry | Searchable, sortable, paged patient table with status badges; add/edit dialog with validation, future-DOB check, phone/email checks, MRN generation and duplicate warning; resized photo BLOB; soft delete/archive; tabbed patient chart with allergy banner and all ten tabs. |
| 2026-11-03 | Phase 4 — Appointments and scheduling | Day/week provider calendar; create/edit/cancel/reschedule with conflict detection; status workflow through check-in, in-progress, completion, no-show and cancellation; waiting-room list with wait times; dashboard schedule and live stat cards from the database. |
| 2026-11-03 | Phase 5 — Clinical encounters | Encounter start from appointment/chart; SOAP editor; vitals with BMI and range highlighting; bundled ICD-10 lookup and diagnosis search; problem/allergy/medication CRUD; signing with service and database immutability plus addendum rows; 30-second draft autosave. |
| 2026-11-03 | Phase 6 — Specialty templates | Six JSON templates (general, cardiology, pediatrics, obgyn, dermatology, ophthalmology) with typed fields, units and ranges; Jackson-backed `TemplateService`; dynamic JavaFX form rendering; values stored in `encounters.template_data`; provider specialty chooses the default template. |
| 2026-11-03 | Phase 7 — Prescriptions | Multi-item prescription builder; autocomplete from a bundled generic-drug list; basic drug/allergy cross-check warning; PDF Rx with clinic header, prescriber and patient details; renew from a previous prescription. |
| 2026-11-03 | Phase 8 — Labs and documents | Lab catalog management in settings; ordering from encounters; result entry with automatic LOW/HIGH/CRITICAL flags; per-test trend chart; SHA-256 document storage, image preview and system PDF opening. |
| 2026-11-03 | Phase 9 — Billing | Service catalog CRUD; invoices created from encounters with discount and configured tax; partial/full payments with automatic status; receipt PDF; patient balance and outstanding invoices on the chart. |
| 2026-11-03 | Phase 10 — Dashboard and reports | Live dashboard statistics; appointment/provider, revenue, diagnosis and demographics reports; JavaFX charts; CSV and PDF export. |
| 2026-11-03 | Phase 11 — Settings, backup and polish | Clinic profile and logo used by all PDFs; online backup, restore with confirmation and daily backups retaining 14 files; global topbar search; Ctrl/Cmd+N/F/S shortcuts; toast notifications, empty states and spinners; light/dark themes; English/Arabic i18n with RTL. |
| 2026-11-03 | Phase 12 — Packaging and release | `package-mac`/`package-win` jpackage profiles with icons, version and vendor; classpath-mode app image (PDFBox 3 is an automatic module, so full-app jlink is not possible) with an explicit runtime module list and `Launcher` entry point; GitHub Actions matrix on JDK 25 for DMG/EXE; README build/run/package/security guide. macOS DMG built and smoke-launched with a temporary data folder. |
| 2026-11-03 | Final verification | `./mvnw -q clean verify` passes on JDK 27 (12 test classes, 65 tests); JaCoCo service coverage 93.93% and repository coverage 79.75%, both above the 70% gate; all FXML contract checks (parse, controller shape, ids, handlers and icon literals) pass. |
