# JSmartClinic

JSmartClinic is a local-first, multi-specialty desktop EMR built with JavaFX, SQLite and plain JDBC.
It runs as a single Java application (no Spring, no web server) and stores all data in a per-user
application-data folder.

## Features

- Patient registry with search, MRN generation, photos, duplicate warnings and soft delete.
- Patient chart with demographics, encounters, problems, allergies, medications, vitals, labs,
  prescriptions, documents and billing in one tabbed view.
- Day/week appointment calendar, conflict detection, status workflow and waiting room.
- SOAP encounter editor with specialty templates, ICD-10 diagnosis search, addenda and signed-note
  immutability.
- Prescription builder with a local drug list, allergy cross-check and printable PDF.
- Lab ordering, result entry, automatic LOW/HIGH/CRITICAL flags and result trend charts.
- Invoices, partial payments, balances, receipts and revenue reports with CSV/PDF export.
- Clinic profile, user management, audit log, backup/restore, light/dark theme and English/Arabic UI.

## Requirements

- JDK 23 or newer. The compiler targets Java 21 bytecode, but JavaFX 25 itself is compiled for
  Java 23, so the build and runtime need a JDK that can load it. CI uses JDK 25 and the build is
  verified locally on JDK 27.
- The included Maven Wrapper (`./mvnw` / `mvnw.cmd`). No separate Maven install is required.
- macOS or Windows for packaging; JavaFX itself is pulled from Maven Central.

## Run

```bash
./mvnw javafx:run
```

On Windows:

```powershell
.\mvnw.cmd javafx:run
```

On first launch the app shows a setup wizard. Create the administrator account and clinic name,
then sign in. There is no default user and no default password.

### Data location

| OS | Default database |
|---|---|
| macOS | `~/Library/Application Support/JSmartClinic/emr.db` |
| Windows | `%APPDATA%\JSmartClinic\emr.db` |
| Linux | `~/.local/share/JSmartClinic/emr.db` |

Set `JSMARTCLINIC_DATA_DIR` to use a different folder, for example for a demo or a portable install:

```bash
JSMARTCLINIC_DATA_DIR=/tmp/jsmartclinic-demo ./mvnw javafx:run
```

Documents are copied to `documents/<sha256>` and backups to `backup/` inside the same folder.
The folder and database file are created with owner-only permissions where the platform supports
POSIX permissions.

## Build and test

```bash
./mvnw -q verify
```

This compiles the JPMS module, runs the JUnit 5/AssertJ suite against temporary SQLite databases,
produces the JaCoCo report in `target/site/jacoco/index.html`, and enforces at least 70% line
coverage for the service and repository packages.

Useful commands:

```bash
./mvnw -q test                       # tests only
./mvnw -q -DskipTests package        # compile and package the JAR
./mvnw -q jacoco:report              # regenerate the coverage report
```

The tests never touch the real application database.

## Package a desktop installer

Packaging uses `jpackage` and must run on the target operating system.

```bash
# macOS -> target/dist/JSmartClinic-1.0.0.dmg
./mvnw -Ppackage-mac -DskipTests package

# Windows -> target/dist/JSmartClinic-1.0.0.exe
.\mvnw.cmd -Ppackage-win -DskipTests package
```

App icons live in `src/main/resources/org/amrosman/jsmartclinic/icons/` (`app.icns` for macOS,
`app.ico` for Windows, `app.png` as the master image).

Both profiles package in `jpackage`'s non-modular (classpath) mode. The build copies the application
JAR and all runtime dependencies to `target/package-input`, adds a `Class-Path` manifest, and uses
`org.amrosman.jsmartclinic.Launcher` as the entry point. PDFBox 3 is an automatic module, so a single
`jlink` image of the whole application is not possible; instead the bundled runtime is limited to the
JDK modules the app needs (`java.base`, `java.desktop`, `java.logging`, `java.sql`, `java.scripting`,
`java.xml`, plus `jdk.unsupported`, `jdk.crypto.ec`, `jdk.localedata` and `jdk.charsets`). The
packaged runtime also receives `-XX:+DisableAttachMechanism` and `--enable-native-access=ALL-UNNAMED`.

Because JavaFX is loaded from the classpath in this mode, it prints one informational warning
(`Unsupported JavaFX configuration: classes were loaded from 'unnamed module'`) on startup. This is
expected and does not affect functionality. The generated installers are unsigned; on macOS sign and
notarize them before distribution, and on Windows expect SmartScreen to warn until the EXE is signed.

## Roles

| Capability | ADMIN | DOCTOR | NURSE | RECEPTION | BILLING |
|---|:-:|:-:|:-:|:-:|:-:|
| Manage users and settings | yes | | | | |
| Register/edit patient demographics | yes | yes | yes | yes | |
| View clinical chart | yes | yes | yes | | |
| Schedule appointments | yes | yes | yes | yes | |
| Record vitals | yes | yes | yes | | |
| Write/sign encounters, diagnoses | | yes | | | |
| Prescribe | | yes | | | |
| Order labs / enter results | | yes | yes | | |
| Invoices and payments | yes | | | yes | yes |
| Reports | yes | yes | | | yes |
| Backup / restore | yes | | | | |
| View audit log | yes | | | | |

Permissions are enforced in the service layer, not only by hiding buttons. Unauthorized service
calls throw `AccessDeniedException`.

## Architecture

```
org.amrosman.jsmartclinic
├── HelloApplication / Launcher   JavaFX entry point
├── app/                          AppContext (manual DI), Session, Navigator
├── domain/                       records and enums
├── db/                           Database, MigrationRunner, migrations
├── repository/                   prepared-statement JDBC repositories
├── service/                      permissions, validation, audit and business rules
├── ui/                           FXML controllers and reusable UI helpers
└── util/                         dates, money, CSV, hashing, i18n
```

- FXML views are in `src/main/resources/org/amrosman/jsmartclinic/views/`; controllers are public
  no-arg classes so the views open in Scene Builder.
- All styling is in `app.css` and `app-dark.css`.
- Database work runs off the JavaFX thread through `Async`/`Task`; UI updates return to
  `Platform.runLater`.
- SQL is always parameterized and lives in repositories, never in controllers.
- Migrations are bundled under `db/migration` and applied in version order inside a transaction.
  The `schema_version` table is managed by `MigrationRunner`.
- Signed encounters are immutable: the service rejects edits and database triggers block direct
  updates, while addenda are inserted as new `AMENDED` rows.

## Backups and restore

Settings → Backup creates an online SQLite backup on demand and keeps the newest 14 files. Automatic
daily backups run at startup when enabled. Restore requires confirmation and replaces the current
database after taking a safety backup.

## Security notes

- Passwords use PBKDF2-HMAC-SHA256 with 210,000 iterations, a 16-byte random salt, constant-time
  comparison and a minimum length of 10 characters.
- Five failed sign-ins lock an account for 15 minutes.
- The app auto-locks after inactivity and requires the current password to unlock.
- The audit log is append-only (database triggers) and records logins, chart views and data changes.
- Data is stored unencrypted in the local SQLite file. Full-disk encryption (FileVault/BitLocker) is
  recommended. SQLCipher-style at-rest encryption is a possible future enhancement, but would
  replace the JDBC driver and require a key-management design.

## Troubleshooting

- **`javafx:run` cannot find JavaFX modules** — run through the Maven wrapper so the JavaFX
  dependencies and module path are resolved by Maven.
- **Permission denied when the database is created** — make sure the data folder is owned by the
  current user and not read-only. The app creates it with owner-only permissions.
- **Restore does not take effect immediately** — close and reopen the app after restoring.
- **CI has no display** — `./mvnw verify` is headless and does not need a display server.
# JSmartClinic
