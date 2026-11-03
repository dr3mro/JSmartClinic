# JSmartClinic — Bug & Issue Fix Plan (for opencode)

> **Agent instructions**
> 1. Do **Step 0** first. Then fix issues **in the order listed** (by batch). Within a batch, the order doesn't matter.
> 2. For every issue: write a **failing test first** (where testable), fix the code, make it pass, and run `./mvnw -q verify`.
> 3. Commit once per issue: `fix(<ID>): <short summary>`.
> 4. Tick the checkbox and add a line to the **Fix Log** at the bottom.
> 5. Don't refactor unrelated code or change the UI look. If a fix needs a schema change, add a **new** migration (`V003__…sql`, `V004__…sql`). Never edit `V001`/`V002`, because existing databases already have them applied.
> 6. Every new migration file must also be added to `db/migration/index.txt` (see B-01).

Review baseline: 2026-11-03. The current build passes (71 tests), and service/repository coverage is about 80–94%. The issues below come from a static review of the code.

Severity: 🔴 Critical (data loss, safety, or security) · 🟠 High (feature broken or wrong data) · 🟡 Medium · ⚪ Low / hygiene

---

## ▶ RESUME HERE (status as of 2026-11-03 01:10)

**Done:** S-0, C-00 → C-08. There are 10 commits; the last is `1215817`. See the Fix Log.
**Done since:** C-09 (`saveResult(orderId, resultId, numeric, text)` reads test, patient and status from the DB; rejects cancelled or mismatched orders). **Previously in progress:** **C-09.** The failing tests are written but **not committed** (`src/test/.../LabServiceTest.java`). They change `saveResult` to `saveResult(orderId, resultId, numeric, text)` and add `resultsCannotBeEnteredOnCancelledOrders`. Next, implement the service and repository change in `LabService.saveResult`/`LabRepository`, then update the callers in `ui/labs/LabResultDialog.java` and `ui/patients/PatientChartController.java`. After that, run `./mvnw -q clean verify` and commit as `fix(C-09)`.
**Remaining (65):** C-09 C-10 C-11 · A-01 → A-15 · B-01 → B-07 · D-01 → D-05 · P-01 → P-05 · U-01 → U-06 · H-01 → H-07 · V-01 → V-18 (Batch 8, visual UI) · F-01 (Batch 9, update checker)

**Migration numbering:** V003–V007 are already used, one migration per fix. **The next migration is `V008__…sql`.** Wherever this plan says "V003", use the next free number instead, and add it to `index.txt`.

**Resume prompt for opencode:**
```
Continue fix-plan.md from the "RESUME HERE" section. First run `git status` and `./mvnw -q clean verify` to confirm the state. Finish C-09 (its tests are already written and uncommitted), then continue in order from C-10. Follow the Agent instructions at the top of fix-plan.md (test first, one commit per issue, new migrations start at V008 and go in index.txt, tick the box and update the Fix Log). Before stopping, update this RESUME HERE section with the new status, the next migration number and the next issue ID. Ask me if a fix needs a design decision.
```

---

## Step 0 — Safety net
- [x] **S-0** The project is **not a git repository** (there is no `.git`). Run `git init`. Add `.DS_Store`, `.idea/workspace.xml`, `*.bak` and `backup/` to `.gitignore`. Delete the stray `.DS_Store` files. Make a baseline commit **before** you change anything else.

---

## Batch 1 — Clinical record integrity 🔴

- [x] **C-00 🔴 Encounters can't be created with the first-run account** — `service/Permission.java`, `FirstRunController`, `EncountersController:87`, `PatientChartController:290`, `AppointmentsController:147`
  - *Problem:* The first-run wizard creates the only account as `ADMIN`. `ADMIN` has no `ENCOUNTERS` (or `PRESCRIBE`) permission, so every "New encounter" and "Start encounter" button is hidden. A solo clinic owner can't write a single note.
  - *Fix:* Add a `clinician` flag to users (`V003`: `ALTER TABLE users ADD COLUMN clinician INTEGER NOT NULL DEFAULT 0`), and add a checkbox labelled "I also see patients (doctor)", plus a specialty field, to the first-run wizard and to the user editor in Settings. `Session.can()` should grant the DOCTOR permissions (`ENCOUNTERS`, `PRESCRIBE`, `LABS`, `VITALS`) when `role==ADMIN && clinician`. `UserRepository.findProviders()` must include clinician admins so they show up as appointment providers. When a button is hidden for permission reasons, show a hint explaining why rather than making it vanish silently.
  - *Tests:* A first-run admin with `clinician=true` can `start()`, `save()` and `sign()` an encounter. A plain admin still gets `AccessDeniedException`.

- [x] **C-01 🔴 Addenda can't be edited, and signed addenda never lock** — `EncounterRepository.insertAmendment`, `EncounterService.amend/sign/save`, `Encounter.signed()`
  - *Problem:* `insertAmendment` creates the addendum with `status='AMENDED'` and `signed_at=now`. `Encounter.signed()` treats `AMENDED` as signed, so the editor opens the new addendum **read-only** and the user can't write anything. On the service side, `save()` only blocks `SIGNED`, so an `AMENDED` row can be overwritten forever. `sign()` keeps the status at `AMENDED`, so the row never locks. Nothing links the addendum to the original note.
  - *Fix:* Add migration `V003`: `ALTER TABLE encounters ADD COLUMN amends_encounter_id INTEGER REFERENCES encounters(id)`. Create addenda as `DRAFT` with `amends_encounter_id` set and `signed_at`/`signed_by` set to NULL. When an addendum is signed, set it to `SIGNED`, and set the **original** to `AMENDED` without changing its content. `save()` must reject any encounter whose `signed_at` is not null. Make `signed()` mean `signedAt != null`. In the chart and editor, show "Addendum to encounter #N" and link the two notes.
  - *Tests:* Amend → the addendum is editable → sign → saving the addendum throws → the original's content is unchanged.

- [x] **C-02 🔴 The DB trigger lets signed content be overwritten** — `V001__init.sql` trigger `encounters_signed_immutable`
  - *Problem:* `WHEN OLD.status='SIGNED' AND NEW.status<>'AMENDED'` lets a single UPDATE change the status to `AMENDED` **and** rewrite the SOAP text at the same time. `AMENDED` rows have no protection, and DELETE isn't blocked at all.
  - *Fix:* In `V003`, drop the trigger and recreate it. Block any UPDATE where `OLD.signed_at IS NOT NULL` that changes `chief_complaint, subjective, objective, assessment, plan, template_data, patient_id, provider_id, encounter_date, signed_at, signed_by`. Allow only the status change from `SIGNED` to `AMENDED`. Add a `BEFORE DELETE` trigger that blocks deleting signed rows.
  - *Tests:* Direct JDBC updates and deletes on a signed row must fail.

- [x] **C-03 🔴 Diagnoses and vitals of signed encounters can still be changed** — `EncounterService.removeDiagnosis`, `removeVital`, `addVital`
  - *Problem:* `removeDiagnosis` and `removeVital` never check whether the encounter is signed. The `patientId` comes from the caller and is never checked, so the audit entry can name the wrong patient.
  - *Fix:* Load the diagnosis or vital, check its encounter's sign status, and get `patientId` from the database. Add DB triggers that block changes to `encounter_diagnoses` and `vitals` rows whose encounter is signed.

- [x] **C-04 🟠 Empty encounters can be signed** — `EncounterService.sign`
  - *Problem:* The check is `chiefComplaint()==null && …`, but `start()` inserts `""`, so the check always passes.
  - *Fix:* Use `isBlank()`, and require at least a chief complaint **or** an assessment/plan.

- [x] **C-05 🟠 Any provider can edit another provider's draft** — `EncounterService.save/sign/deleteDraft`
  - *Fix:* Only the draft's `provider_id` may save, sign or delete it. Add an explicit "take over draft" action, available to DOCTOR and ADMIN, that writes an audit entry.

- [x] **C-06 🟠 Autosave can silently drop edits** — `EncounterEditorController.persist`
  - *Problem:* `dirty=false` is set when a save **finishes**, so anything typed while that save was in flight is never marked unsaved. It is lost if the user then leaves or the session locks.
  - *Fix:* Keep an edit counter. Record it when building the payload, and clear `dirty` only if the counter hasn't changed since.

- [x] **C-07 🟠 Leaving the editor from the sidebar or the lock screen discards unsaved changes**
  - *Problem:* Only `onBack()` asks for confirmation. Sidebar navigation, global search, logout and the inactivity lock all drop the draft without asking.
  - *Fix:* Add a `Navigator.CanLeave` interface that `EncounterEditorController` implements. It saves silently, or asks first. `Navigator.show()` and `MainController` (logout and lock) must call it before switching views.

- [x] **C-08 🔴 "Critical" lab flags are arbitrary** — `LabService.flagFor`, `V002__seed.sql`
  - *Problem:* "Critical" is calculated as 50% of the reference span beyond the limits. For example, fasting glucose 70–99 is flagged critical above 113.5, CRP 0–5 above 7.5, and HbA1c above 6.4. That's clinically wrong, and it trains users to ignore the flag.
  - *Fix:* `V003`: `ALTER TABLE lab_tests ADD COLUMN crit_low REAL; ADD COLUMN crit_high REAL`. Flag CRITICAL **only** when an explicit critical limit is crossed. Otherwise flag LOW, HIGH or NORMAL. Add critical-limit fields to the lab catalogue editor in Settings. Seed only well-known limits (e.g. K <2.5/>6.5, Na <120/>160, glucose <40/>450), each with a comment.
  - *Tests:* Glucose 130 is HIGH, not CRITICAL. K 6.8 is CRITICAL.

- [x] **C-09 🟠 Lab results can be entered on a cancelled order, or against the wrong test** — `LabService.saveResult`
  - *Problem:* It trusts the `testId` and `patientId` from the caller and never checks that `resultId` belongs to `orderId`. A cancelled order gets silently reopened as COLLECTED or RESULTED.
  - *Fix:* Load the result row with its order, and take the test and patient from the database. Reject the save if the order is `CANCELLED`.

- [ ] **C-10 🟡 Deleting a lab test that has results crashes** — `LabRepository.deleteTest`
  - *Problem:* The foreign key raises a raw SQL error.
  - *Fix:* Add an `active` column (`V003`). Deactivate tests instead of deleting them, and hide inactive tests from ordering.

- [ ] **C-11 🟡 Clinical history is hard-deleted** — `ClinicalRepository.removeProblem`, `DocumentRepository.delete`
  - *Fix:* Soft-delete instead: set `deleted_at` (add the column in `V003`), and filter deleted rows out of the lists.

---

## Batch 2 — Security & access control 🔴 (AppSec)

- [ ] **A-01 🔴 Lockout re-triggers on the first wrong password after it expires** — `AuthService.login`
  - *Problem:* `failed_logins` is never reset when a lock expires. One wrong password at count 5 or more locks the account for another 15 minutes.
  - *Fix:* If `locked_until` has passed, reset the counter to 0 before counting the new failure.
  - *Tests:* Lock the account, set the clock forward past the lock, enter 1 wrong password → not locked.

- [ ] **A-02 🔴 Lock-screen and session-timer leaks after logout and login** — `MainController`
  - *Problem:* Every login creates a new `MainController` and calls `installShortcuts()` on the **same reused Scene**. The old controllers' `PauseTransition` timers and scene event filters are never removed. A stale timer can show the lock dialog for the next user, and cancelling it logs that user out. Filters and accelerators also pile up.
  - *Fix:* Add `MainController.dispose()`. It stops `inactivity` and removes the event filters (store their handler references) and accelerators. `HelloApplication` calls it before `showLogin()`. Alternatively, create a new `Scene` for each login.

- [ ] **A-03 🟠 Mouse activity doesn't reset the inactivity lock** — `MainController.installShortcuts`
  - *Problem:* The mouse filter only calls `session.touch()`, never `resetInactivity()`. A user who only clicks gets locked after 10 minutes of active use.
  - *Fix:* Call `resetInactivity()` from the mouse filter as well, throttled to once a second.

- [ ] **A-04 🟠 The lock dialog leaves patient data visible, and one typo logs the user out** — `MainController.showLockDialog`
  - *Fix:* Cover the content with an opaque or blurred overlay while locked. Allow 3 attempts, and count failures with the same lockout logic as login. Save drafts first (C-07). Run password checking off the FX thread.

- [ ] **A-05 🟠 Login and the lock screen run PBKDF2 (210k iterations) on the FX thread** — `LoginController.onLogin`, `FirstRunController`, `showLockDialog`
  - *Fix:* Use `Async.run`, and disable the button and show a spinner while it runs.

- [ ] **A-06 🟠 Clinical data reaches roles without VIEW_CLINICAL** — `PatientService.allergySummary`, `problemSummary`, `search/count/recent/basic/findByMrn/duplicates`, and the `SchedulingService` read methods
  - *Problem:* There are no permission checks, not even for being logged in. BILLING and RECEPTION can read allergy and problem summaries.
  - *Fix:* Add `session.require(Permission.VIEW_CLINICAL)` to the summaries. Add a `requireLoggedIn()` helper and use it for the demographic searches. Add a service-level test for every public service method, for each role.

- [ ] **A-07 🟠 Clinical writes are guarded by a read permission** — `PatientService.add/update/removeAllergy|Problem|Medication`
  - *Problem:* Writes check `VIEW_CLINICAL`.
  - *Fix:* Add a `CLINICAL_EDIT` permission for DOCTOR and NURSE. Use `PRESCRIBE` or `ENCOUNTERS` for medication changes if you prefer. Update the matrix in `EMR_PLAN.md` §5.

- [ ] **A-08 🟡 Insecure direct object references (IDOR) and untrusted `patientId` in clinical CRUD** — `removeAllergy(id, patientId)` and similar
  - *Problem:* Rows are changed by `id` alone, and the caller's `patientId` is written to the audit log.
  - *Fix:* Use `WHERE id=? AND patient_id=?` and check the rows-affected count; throw if it is 0. Use the patient id from the database in the audit entry.

- [ ] **A-09 🟡 Failed logins and lockouts aren't audited, and audit writes aren't atomic** — `AuthService`, `AuditService`
  - *Fix:* Log `LOGIN_FAILED` and `ACCOUNT_LOCKED` (with the username only, never the password). Pass the `Connection` to `AuditRepository.insert` so the change and its audit row share one transaction.

- [ ] **A-10 🟡 Username enumeration** — `AuthService.login`
  - *Problem:* "Inactive" and "locked" messages reveal that an account exists, and the timing differs for unknown users.
  - *Fix:* Return one generic message. For unknown users, still run PBKDF2 against a dummy hash.

- [ ] **A-11 🟡 Admins can lock everyone out** — `UserService.update`, `resetPassword`
  - *Fix:* Block demoting or deactivating the **last active ADMIN**. `resetPassword` should also clear `failed_logins` and `locked_until`. Make usernames case-insensitive (`COLLATE NOCASE` via `V003` and a unique index on `LOWER(username)`). If the logged-in user's own role changes, refresh the session.

- [ ] **A-12 🟡 CSV formula injection** — `util/Csv.escape`
  - *Problem:* Exported patient names such as `=HYPERLINK(...)` run as formulas in Excel.
  - *Fix:* Prefix cells that start with `= + - @ \t \r` with `'`.
  - *Tests:* Cover it in `UtilAndTemplateTest`.

- [ ] **A-13 🟡 Backup restore is unsafe** — `BackupService.restore`, `SettingsController.onRestore`
  - *Problem:* It overwrites the live DB file while other connections may be open. It doesn't check that the file is a valid SQLite DB, doesn't run an integrity check, doesn't check the schema version, and doesn't back up the current DB first. Afterwards the app keeps running with stale cached views and a session user who may not exist in the restored DB.
  - *Fix:* Open the backup read-only and run `PRAGMA integrity_check`. Check `schema_version` is less than or equal to the current version. Make an automatic "pre-restore" backup. Restore with the SQLite backup API (`restore from`) instead of a file copy. Then log out and force the app to restart (or close and reopen the `AppContext`).

- [ ] **A-14 🟡 Documents & exports** — `DocumentService`
  - *Problem:* `readAllBytes` reads the whole file into memory and there is no size limit. Deleted documents leave the file on disk. Generated PDFs pile up in `exports/` forever.
  - *Fix:* Stream the SHA-256 calculation. Limit files to 25 MB. Delete the file when no row references its hash any more. Clean `exports/` on startup (delete files older than 7 days).

- [ ] **A-15 ⚪ Hardening** — `pom.xml` jpackage profiles
  - *Fix:* Add `-XX:-EnableDynamicAgentLoading`. Replace `java.se` with an explicit module list that excludes `java.instrument`. Make `Session.current` `volatile`, because it is read from `Async` threads.

---

## Batch 3 — Billing correctness 🟠

- [ ] **B-01 🟠 Migrations rely on a hand-maintained `index.txt`** — `MigrationRunner.discover`
  - *Problem:* During development (`file:` URLs) the folder is scanned. In the packaged JAR or DMG only `index.txt` is read. A forgotten entry means the migration **silently never runs in production**.
  - *Fix:* Add a unit test that fails if any `V*.sql` file is missing from `index.txt`. Also roll back explicitly when a migration fails.

- [ ] **B-02 🟠 Adding an item to an invoice doesn't recalculate tax or status** — `BillingRepository.recalculate`
  - *Problem:* It reuses the old `tax_cents` (and has an unused `taxRate` variable). The total can go negative. A PAID invoice stays PAID after a new item is added.
  - *Fix:* Store `tax_rate` on the invoice (`V003`) and recalculate the tax. Clamp the total at 0 or higher. Recalculate the status from the payments. Refuse `addItem` on `VOID` invoices, and on `PAID` ones unless the user explicitly confirms.

- [ ] **B-03 🟠 Input validation** — `BillingService.createInvoice/addItem/saveService`
  - *Fix:* Reject a negative discount, a discount larger than the subtotal, qty ≤ 0, and a negative price.

- [ ] **B-04 🟠 Overpayment race** — `BillingService.recordPayment`
  - *Problem:* The balance check and the insert run in separate transactions, so a double click can pay twice.
  - *Fix:* Do the check and the insert in one `BEGIN IMMEDIATE` transaction in the repository. Disable the Pay button while the payment is processing.

- [ ] **B-05 🟡 Invoices with payments can be voided** — `voidInvoice`
  - *Fix:* Refuse if payments exist, or require a refund record first. Store the void reason and the user who voided it.

- [ ] **B-06 🟡 Money parsing** — `Money.parseToCents`
  - *Problem:* Inputs like `"12.3.4"` or `"-"` throw `NumberFormatException`. Letters are silently stripped (`"abc12"` becomes 12). It uses `double`.
  - *Fix:* Parse with `BigDecimal` and a locale-aware format, and throw `IllegalArgumentException("Invalid amount")`. Also, `Money.format` of negative values currently gives `$-5.00`; make it give `-$5.00`.

- [ ] **B-07 ⚪ Integer-only tax rate**
  - *Fix:* Store the tax rate in basis points so rates like 7.5% work.

---

## Batch 4 — Scheduling 🟡

- [ ] **D-01 🟠 The wait time is wrong** — `SchedulingService.minutesWaiting`
  - *Problem:* It's measured from the **scheduled start**, not from check-in.
  - *Fix:* Add `checked_in_at` in `V003` and set it in `changeStatus(CHECKED_IN)`.
- [ ] **D-02 🟡** `update()` can reschedule appointments that are COMPLETED, CANCELLED or NO_SHOW, and doesn't null-check start and end. Block both.
- [ ] **D-03 🟡** The provider isn't checked: it must be an active DOCTOR or NURSE. The patient must exist and not be archived.
- [ ] **D-04 🟡** The conflict check and the insert or update aren't atomic. Do both in one transaction.
- [ ] **D-05 ⚪** `enrich()` runs a separate query for each row, and `patients.findById` loads the photo BLOB each time. Use a JOIN, or a lightweight patient projection without `photo`.

---

## Batch 5 — PDFs, dates & data 🟠

- [ ] **P-01 🔴 PDF generation crashes on newlines and loses non-Latin text** — `PdfService.safe/write`
  - *Problem:* `safe()` keeps `\x00–\xFF`, so `\n` and `\t` reach `showText()`, and Helvetica throws `IllegalArgumentException`. An Rx with multi-line instructions **fails to print**. Arabic names and symbols (and `€`) turn into `????`.
  - *Fix:* Embed a Unicode TTF (e.g. Noto Sans plus Noto Naskh Arabic in `resources/fonts/`) with `PDType0Font.load`. Split text on line breaks and remove other control characters. Add basic RTL handling for Arabic (ICU4J `Bidi`, or document the limitation). Use a monospaced font, or real columns, for the invoice table: `String.format` padding doesn't line up with a proportional font.
  - *Tests:* Rx with `"take 1\nafter meals"` and an Arabic patient name → the PDF is created without throwing.

- [ ] **P-02 🟡 Timestamps don't sort correctly as text** — `Dates.toText`
  - *Problem:* `ISO_INSTANT` leaves out the fraction when it is zero (`…:00Z` vs `…:00.500Z`). The database compares these as text (`start_at < ?`, `paid_at >= ?`, `ORDER BY`), so instants within the same second can sort wrongly.
  - *Fix:* Always write a fixed-width format, `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`. In `V003`, rewrite existing values to the same format (`strftime('%Y-%m-%dT%H:%M:%fZ', col)`). Also `BackupService` writes `last_backup_at` with `Instant.toString()`; use `Dates.toText` there too.

- [ ] **P-03 🟡 Searches load photo BLOBs** — `PatientRepository.COLUMNS`
  - *Problem:* Every list and search query selects `photo`.
  - *Fix:* Use a list projection without `photo`, and load the photo only in `chart()` and the edit dialog.

- [ ] **P-04 🟡 Unescaped LIKE wildcards** — `PatientRepository.search`, `Icd10Service`
  - *Fix:* Escape `%`, `_` and `\`, and add `ESCAPE '\'`. Also match "last first" name order.

- [ ] **P-05 🟡 Repository updates ignore the rows-affected count**
  - *Fix:* Make `Jdbc.update` callers throw `IllegalArgumentException("… not found")` when 0 rows change.

---

## Batch 6 — UI responsiveness & correctness 🟡

- [ ] **U-01 🟠 Global search queries the DB on the FX thread on every keystroke** — `MainController.setupSearch`
  - *Problem:* It also throws `AccessDeniedException` for roles without access once A-06 is fixed.
  - *Fix:* Debounce by 250 ms with a `PauseTransition`, run the query with `Async`, ignore out-of-date results, and hide the search for roles that can't search.

- [ ] **U-02 🟡 Other DB calls on the FX thread**
  - *Problem:* `LabOrderDialog` (`context.labs().tests()` in its constructor) and `PatientDialog` (photo resize) call the DB directly, and every other `context.<service>()` call outside `Async.run` needs checking.
  - *Fix:* Run `grep -rn "context\.[a-z]*()\.[a-z]*(" src/main/java/**/ui` and move each direct call into `Async`.

- [ ] **U-03 🟡 Views load their data twice on first open**
  - *Problem:* The controllers load in `initialize()`, and then `Navigator` immediately calls `refresh()`.
  - *Fix:* Load only in `refresh()`.

- [ ] **U-04 🟡 `Navigator.refresh()` is dead code**
  - *Problem:* It removes the cached view and then does nothing with it.
  - *Fix:* Make it reload the current view, or delete it.

- [ ] **U-05 🟡 i18n is mostly missing**
  - *Problem:* Only 51 keys exist, mostly navigation. Choosing Arabic flips the layout to RTL, but almost all text stays English.
  - *Fix:* Either move all FXML and controller strings into `messages*.properties` (use `%key` in FXML), or hide the Arabic option until it's complete.

- [ ] **U-06 ⚪** Disable the Save, Sign, Pay and Order buttons while their `Async` task runs, so a double click can't submit twice.

---

## Batch 7 — Build & repo hygiene ⚪

- [ ] **H-01** Delete the dead `hello-view.fxml` and `HelloController.java`. Optionally rename `HelloApplication` to `JSmartClinicApp` and update `pom.xml`, `Launcher` and `module-info`.
- [ ] **H-02** Delete `pom.xml.bak` and `backup/`, now that git is the safety net.
- [ ] **H-03** `target/surefire-reports` still contains a stale `UiSmokeManualTest` whose source no longer exists. Run `./mvnw clean verify` in CI.
- [ ] **H-04** The packaged app runs JavaFX from the **classpath**, which is an unsupported configuration and logs a startup warning. Package it as a module (`--module-path` plus `--module org.amrosman.jsmartclinic/...Launcher`), or keep the classpath and suppress the warning, with a note explaining why.
- [ ] **H-05** CI's `macos-latest` builds only an **arm64** DMG. Add a `macos-13` (Intel) job if you need to support Intel Macs.
- [ ] **H-06** In `Database`, the `:memory:` path handling is broken: `toAbsolutePath()` turns it into a real file named `:memory:`. Fix it, or remove the special case.
- [ ] **H-07** Add a `MigrationRunner` test that applies all migrations to a **copy of a database at version 2**. This proves `V003+` upgrade existing installs.

---

## Batch 8 — Visual UI review (dark theme, 1360×860, reviewed 2026-11-03) 🟡

> These findings come from screenshots of the packaged app. Fix them after Batch 7, unless one blocks a workflow (V-01 and V-02 do).

- [ ] **V-01 🟠 The Appointments day view looks fully booked when it's empty** — `views/appointments.fxml`, `AppointmentsController`
  - *Problem:* Every hour slot (08:00 onward) draws as a solid green block across the whole day, so an empty day looks booked. The day header ("Tuesday · 3 Nov") is hidden behind the block.
  - *Fix:* Empty slots should be transparent with only thin hour lines, plus a hover highlight. Only real appointments get a colored card, with the patient, time and status shown. Keep the day header above the grid.
- [ ] **V-02 🟠 Toolbar buttons are cut off** — Appointments and Lab Results toolbars
  - *Problem:* Labels are truncated: `…`, `W…`, `To…`, `Chec…`, `Start v…`, `Compl…`, `Can…`, `+ New appointm…`, `3 li…`, `Refr…`, `Tr…`, `Mark collec…`, `Enter re…`, `Cancel or…`, `+ Order l…`.
  - *Fix:* Set `minWidth="-Infinity"` (use the preferred size) on these buttons. Wrap the toolbar in a `FlowPane`, or put secondary actions (Cancel, Complete) in an overflow `MenuButton`. Use icon and label pairs with tooltips. The buttons must not truncate at the minimum window width of 1100px.
- [ ] **V-03 🟠 Date pickers can't be read in dark mode** — Appointments and Reports (`From`/`To`)
  - *Problem:* The `DatePicker` editor has a white background with light grey text.
  - *Fix:* In `app-dark.css`, style `.date-picker .text-field`, `.date-picker .arrow-button` and the popup (`.date-picker-popup`) to match the other text fields.
- [ ] **V-04 🟡 Disabled buttons are almost invisible, and Archive reads as "danger"** — Patients, Billing, Labs, Prescriptions
  - *Problem:* Disabled buttons drop to about 20% contrast, so users can't tell which actions exist.
  - *Fix:* Use about 45% opacity while disabled, and keep the text legible. Show a tooltip explaining why ("Select a patient first"). The `Include archived` checkbox label looks disabled too, so fix its color.
- [ ] **V-05 🟡 Empty charts show fake axes** — Reports
  - *Problem:* With no data, Revenue, Appointments by provider and Top diagnoses draw a 0–150 axis and a flat line. Demographics shows a single dot.
  - *Fix:* Show the same empty state used elsewhere ("No data for this period"). Set the axis range from the data. Show the pie legend in readable form ("Male", not `MALE`), with counts and percentages.
- [ ] **V-06 🟡 Duplicate actions and search fields**
  - *Problem:* The Dashboard has **New Patient** twice (the top bar and the Recent patients card). Patients has **New Patient** twice and **two search boxes** (global and page).
  - *Fix:* Keep the global search in the top bar, and on the Patients page make it filter the table, or drop the page search. Remove duplicate New Patient buttons, keeping the top-bar one.
- [ ] **V-07 🟡 The top bar sits above the sidebar** — `main-view.fxml`
  - *Problem:* The page title bar spans the full width, and the "JSmart Clinic" brand starts *below* it, which looks misaligned.
  - *Fix:* Make the sidebar full height (`BorderPane left`), with the top bar only in the content area (`center` = `VBox(topbar, contentArea)`).
- [ ] **V-08 🟡 The window title repeats itself**
  - *Problem:* The title reads "JSmartClinic — JSmartClinic" when the clinic name is the default.
  - *Fix:* Show only "JSmartClinic" when the clinic name is blank or equals the default.
- [ ] **V-09 🟡 Placeholder clinic details are seeded and printed on PDFs** — Settings → Clinic profile
  - *Problem:* "1 Health Street", "+1 555 0100" and `front-desk@jsmartclinic.local` come pre-filled, so they print on real prescriptions and receipts.
  - *Fix:* Seed these as empty. Make the first-run wizard ask for them. Before generating a PDF, warn if the clinic profile is incomplete.
- [ ] **V-10 🟡 A status label is cut off** — Settings → Preferences card
  - *Problem:* The "Loaded…" status text under Backups is clipped by the bottom of the card.
  - *Fix:* Add bottom padding, or move the status next to the Save button.
- [ ] **V-11 ⚪ Empty-row striping** — Patients, Encounters, Prescriptions, Labs, Recent patients
  - *Problem:* Tables draw a dozen empty striped rows below the data.
  - *Fix:* Hide the striping on empty rows (`.table-row-cell:empty { -fx-background-color: transparent; -fx-border-color: transparent; }`).
- [ ] **V-12 ⚪ Truncated column headers and values**
  - *Problem:* "Dose / frequ…" (Prescriptions) and "3.5 – 5.1 mm…" (Labs reference) are cut off.
  - *Fix:* Use wider columns or wrapping header labels, and a tooltip with the full value.
- [ ] **V-13 ⚪ The Labs pending count is ambiguous**
  - *Problem:* It shows "1 pending" next to 3 lines. Label it "1 order pending (3 tests)".
- [ ] **V-14 🟠 Table rows can't be selected through accessibility** (VoiceOver and automation)
  - *Problem:* Setting `AXSelected` on a patient row doesn't update the JavaFX selection, so Open chart and Edit stay disabled. This also blocks screen-reader users.
  - *Fix:* Double-click and Enter on a row should open it. Check that each `TableView` works with keyboard navigation (arrow keys, then Enter). Consider an "Open" link button in each row.
- [x] **V-16 🟠 Login screen** *(fixed by hand on 2026-11-03, uncommitted: `maxHeight="-Infinity"` on the login and first-run cards, `.auth-brand .brand` dark text, new prompt texts. Still to do: hide the clinic chip when the name is the default, and a dark card in dark mode)* — `views/login.fxml`, `app.css`/`app-dark.css`
  - *Problem:* The brand shows only **"Clinic"**, because "JSmart" is white text on the white card (it uses the sidebar's `.brand` style). The card runs to the bottom edge of the window and gets clipped: it isn't vertically centred and has a large empty area below the form. The password prompt text is a row of dots (`••••••••••`), which looks like a saved password. The username hint "e.g. admin" hints at a guessable account name. The "JSmartClinic" chip repeats the brand.
  - *Fix:* Add a login-specific `.login-brand` style with dark text for "JSmart". Centre a fixed-height card (about 460px) with `StackPane` alignment and margins. Use prompt texts "Enter your username" and "Enter your password". Show the clinic name chip only when a custom clinic name is set. Make the card follow the theme too (dark card in dark mode).
- [x] **V-17 🔴 Dark-theme colours: white text areas and date fields, low contrast** *(quick CSS fix applied by hand on 2026-11-03, uncommitted; the root-cause fix below is still open)*
  - *Problem:* In the encounter editor (dark theme), the Subjective, Objective, Assessment and Plan text areas, "Previous pregnancies", and every `DatePicker` render **bright white**, so light text on them is unreadable. **Root cause:** `main-view.fxml`, `login.fxml` and `first-run.fxml` set `stylesheets="@../app.css"` on their root node. Node-level stylesheets **outrank** the scene stylesheets that `ThemeManager` adds, so light rules (e.g. `.text-area.form-field .content`) beat `app-dark.css`. The dark theme also never sets `-fx-control-inner-background`.
  - *Measured contrast (WCAG AA needs 4.5:1 for small text):* `.section-label` was 3.7:1 (dark) and 2.6:1 (light); white text on `.primary-button` `#0d9488` was 3.7:1. The quick fix raises these to 6.8:1, 4.8:1 and 5.5:1.
  - *Quick fix applied:* `!important` overrides at the end of `app-dark.css` (looked-up colours on `.root`, the text area content, the date-picker editor, prompt text, section labels, the primary button), plus contrast fixes at the end of `app.css`.
  - *Also fixed:* on the login and first-run card (always white), the dark theme made typed text and password dots light grey on a light field. Added `.auth-card` overrides with dark text (`#0f172a`, 17:1).
  - *Proper fix (to do):* Make `ThemeManager.apply()` set the theme on the **root node** (`root.getStylesheets().setAll(app.css[, app-dark.css])`) instead of only on the scene, or remove `stylesheets=` from those root FXMLs (keep it for Scene Builder through `fx:define` or a preview-only setting). Then remove the `!important` rules. Define the palette once as looked-up colours (`-c-bg`, `-c-surface`, `-c-text`, `-c-muted`, `-c-accent`) on `.root` in each theme, and reference them everywhere. Add a test that loads every FXML under the dark theme and checks that no `TextInputControl` has a white background.
- [ ] **V-18 🟠 Encounter editor details**
  - *Problem:* It defaulted to the **Obstetrics & Gynaecology** template for a **male** patient seen by a **Cardiology** doctor; the default should come from the provider's specialty and never pick sex-specific templates that don't fit. The chief complaint field has **no label**. Numeric count fields (Gravida, Para, Abortions) show prompts like "0.0 – 30.0", which look like entered values and suggest decimals for whole-number counts.
  - *Fix:* Choose the default template from the provider's specialty, and filter out sex-specific templates that don't match the patient. Add a "Chief complaint" label. Use integer spinners for counts, and prompts like "e.g. 2" in the prompt-text colour.
- [x] **V-19 🟠 The patient dialog was taller than the screen** *(fixed by hand on 2026-11-03, uncommitted)*
  - *Problem:* It was one 18-row label/field grid with no scroll pane, so on a laptop screen the bottom fields and the Register/Cancel buttons ended up off-screen.
  - *Fix applied:* Added a new `ui/components/ResponsiveForm`. Fields are grouped into sections (Identity, Contact, Emergency contact, Insurance, Notes), each with its label above the field. The layout uses **2 columns**, dropping to **1 column under 620px**. The form scrolls inside the dialog, which is capped at 90% of the current screen's height (and 92% of its width) and can be resized; the buttons always stay visible. The photo and MRN header is now compact.
  - *To do:* Use `ResponsiveForm` for the other long dialogs (appointment, invoice, prescription, user), and delete the now-unused `Forms.row`/`Forms.grid` once nothing uses them.
- [ ] **V-15 ⚪ Still to review (needs a click-through)**
  - Patient chart tabs, the patient, appointment, invoice and payment dialogs, the first-run screen, the lock dialog, the light theme, Arabic/RTL, and the 1100×700 minimum window size.

---

## Batch 9 — Features 🟢

- [ ] **F-01 🟢 In-app update check from GitHub Releases**
  - *Goal:* On startup (and from Settings → "Check now"), the app checks `https://api.github.com/repos/dr3mro/JSmartClinic/releases/latest`. If a newer version exists, it offers the update without blocking work.
  - *Current version:* Read it from the `jpackage.app-version` system property, which jpackage sets in packaged apps. Fall back to the JAR manifest's `Implementation-Version`, or `dev` when running from IntelliJ, in which case don't check. Add `<addDefaultImplementationEntries>true</addDefaultImplementationEntries>` to the jar plugin manifest so the manifest has the version.
  - *Implementation:* Add a new `service/UpdateService` using `java.net.http.HttpClient` (add `requires java.net.http;` to `module-info.java`), and parse the response with Jackson. Run the check in the background through `Async`, with a 5 s timeout. Check at most once every 24 h (`settings.last_update_check`). Never show an error dialog for this; only log failures.
  - *Version compare:* Compare semantic versions (`1.10.0 > 1.9.2`). Tags look like `v1.2.0`. Skip `prerelease`/`draft` releases unless the "Include beta versions" setting is on.
  - *UI:* Show a dismissible banner at the top of the main window: "JSmartClinic 1.2.0 is available — **What's new** · **Download** · Skip this version · Later". "What's new" shows the release `body` (markdown as plain text) in a dialog. Settings → General gets the checkbox "Check for updates automatically" (on by default), a "Check now" button, the last-checked time and the current version.
  - *Download:* Pick the asset by OS: `.dmg` on macOS, `.exe`/`.msi` on Windows (match the arch where possible). Stream it to the user's Downloads folder with a progress bar and a Cancel button.
  - *Integrity check:* Verify its SHA-256 against the release's `SHA256SUMS.txt` asset; on a mismatch, delete the file and warn. Then open the installer with `Desktop.open` and ask the user to quit the app. Never install silently or replace files in place.
  - *Security:* HTTPS only. Allow only the hosts `api.github.com`, `github.com` and `objects.githubusercontent.com` (check the host after each redirect). Cap downloads at 300 MB.
  - *Authenticity:* `SHA256SUMS.txt` only catches corruption, because it comes from the same release. To prove authenticity, add release signing: an Ed25519 signature `SHA256SUMS.txt.sig`, made in CI from a repository secret and verified against a public key embedded in the app. Later, add Apple notarization and Windows Authenticode signing.
  - *Privacy & data safety:* Send no patient data and no identifiers (a generic `User-Agent: JSmartClinic/<version>`). Before opening the installer, run `BackupService.backupNow()` so that a database migration in the new version is reversible.
  - *Private repo:* If the GitHub repo is private, the API needs a token. Either make releases public (or use a separate public "releases" repo), or the feature can't work for end users. Decide this before implementing.
  - *Tests:* Version comparison; parsing a recorded `releases/latest` JSON fixture; asset selection per OS; "skip this version"; the 24 h throttle; checksum mismatch → file deleted; a disallowed redirect host is rejected. Use a local `HttpServer` stub, never the real network.
  - *Release process (add it to the README):* Bump `<app.version>` in `pom.xml`, commit, then `git tag v1.1.0 && git push origin v1.1.0`. CI builds the DMG and EXE and publishes them as a GitHub Release together with `SHA256SUMS.txt`.

---

## Definition of Done
1. Every box is ticked, and each fix has a test where one is possible.
2. `./mvnw -q clean verify` passes, and service/repository coverage is still at least 70%.
3. The app starts on a **fresh** data folder **and** on an existing `emr.db` from before these fixes (migration path).
4. Manual smoke test: log in → register a patient → book an appointment → check in → write an encounter → sign → add an addendum → sign it → write an Rx with multi-line instructions and print the PDF → order labs → enter a result → create an invoice → take a partial payment → back up → restore → the app restarts.

## Fix Log
| Date | ID | Commit | Notes |
|---|---|---|---|
| 2026-11-03 | S-0 | 7be2cdb | Initialised git, updated `.gitignore`, removed stray `.DS_Store` files, baseline commit. |
| 2026-11-03 | C-00 | 8bd370d | Added `users.clinician` (V003), clinician-admin permissions, first-run/settings controls, provider lookup, and permission hints. New `ClinicianAdminTest`. |
| 2026-11-03 | C-01 | 7f2b086 | Added `encounters.amends_encounter_id` (V004); addenda start as editable drafts, `signed()` uses `signed_at`, signing links and locks both notes. Updated encounter tests and UI links. |
| 2026-11-03 | C-02 | c9f3f45 | Replaced the signed-encounter trigger (V005): signed content and metadata are immutable, only `SIGNED -> AMENDED` is allowed, and signed rows cannot be deleted. Extended JDBC trigger tests. |
| 2026-11-03 | C-03 | 52feecb | Diagnosis and vital writes now load the stored row, reject signed encounters, derive the audit patient from the database, and are guarded by new triggers (V006). |
| 2026-11-03 | C-04 | 040972f | Signing now uses `isBlank()` and requires a chief complaint or a non-blank assessment/plan. Added an empty/whitespace signing test. |
| 2026-11-03 | C-05 | 9ac80c7 | Save/sign/delete now require the draft's provider; added audited `takeOverDraft()` for doctors/admins and a "Take over draft" editor action. |
| 2026-11-03 | C-06 | a1cc44f | Added an editor change counter; autosave clears the dirty flag only when no edits arrived while the save was in flight. |
| 2026-11-03 | C-07 | 33c889e | Added `Navigator.CanLeave`; navigation, logout and lock now ask the editor to save/confirm first. Automatic locks save silently and never wait for a prompt. New `NavigationGuardTest`. |
| 2026-11-03 | CI | (this commit) | `build.yml`: JDK 25; `package-mac`/`package-win` matrix; version taken from the tag (`v1.2.0` → `1.2.0`, otherwise `1.0.<run>`); pushing a `v*` tag now publishes a **GitHub Release** with the DMG, the EXE and `SHA256SUMS.txt`. Main-branch builds still upload run artifacts. |
| 2026-11-03 | C-09 | (this commit) | `LabService.saveResult` now takes only order and result IDs, loads the row with `LabRepository.resultRow`, rejects cancelled or mismatched orders, and audits the stored patient. Added `AppContext.auditEvents()`. |
| 2026-11-03 | C-08 | 5671184 | Added explicit `crit_low`/`crit_high` limits (V007) seeded for potassium, sodium and glucose. CRITICAL now requires crossing a critical limit; the Settings editor and result preview use the same logic. |
