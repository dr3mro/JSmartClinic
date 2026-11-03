package org.amrosman.jsmartclinic.app;

import org.amrosman.jsmartclinic.db.Database;
import org.amrosman.jsmartclinic.db.MigrationRunner;
import org.amrosman.jsmartclinic.repository.AppointmentRepository;
import org.amrosman.jsmartclinic.repository.AuditRepository;
import org.amrosman.jsmartclinic.repository.BillingRepository;
import org.amrosman.jsmartclinic.repository.ClinicalRepository;
import org.amrosman.jsmartclinic.repository.DocumentRepository;
import org.amrosman.jsmartclinic.repository.EncounterRepository;
import org.amrosman.jsmartclinic.repository.LabRepository;
import org.amrosman.jsmartclinic.repository.PatientRepository;
import org.amrosman.jsmartclinic.repository.PrescriptionRepository;
import org.amrosman.jsmartclinic.repository.ReportRepository;
import org.amrosman.jsmartclinic.repository.SettingsRepository;
import org.amrosman.jsmartclinic.repository.UserRepository;
import org.amrosman.jsmartclinic.service.AuditService;
import org.amrosman.jsmartclinic.service.AuthService;
import org.amrosman.jsmartclinic.service.BackupService;
import org.amrosman.jsmartclinic.service.BillingService;
import org.amrosman.jsmartclinic.service.DocumentService;
import org.amrosman.jsmartclinic.service.DrugCatalog;
import org.amrosman.jsmartclinic.service.EncounterService;
import org.amrosman.jsmartclinic.service.Icd10Service;
import org.amrosman.jsmartclinic.service.LabService;
import org.amrosman.jsmartclinic.service.PatientService;
import org.amrosman.jsmartclinic.service.PdfService;
import org.amrosman.jsmartclinic.service.PrescriptionService;
import org.amrosman.jsmartclinic.service.ReportService;
import org.amrosman.jsmartclinic.service.SchedulingService;
import org.amrosman.jsmartclinic.service.SettingsService;
import org.amrosman.jsmartclinic.service.TemplateService;
import org.amrosman.jsmartclinic.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;

/**
 * Manual dependency injection container. Holds the single database, the session and all
 * repositories/services used by the UI.
 */
public final class AppContext {

    private static final Logger log = LoggerFactory.getLogger(AppContext.class);
    private static AppContext instance;

    private final Database database;
    private final Session session = new Session();
    private final Navigator navigator = new Navigator(this);

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final ClinicalRepository clinicalRepository;
    private final AppointmentRepository appointmentRepository;
    private final EncounterRepository encounterRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final LabRepository labRepository;
    private final DocumentRepository documentRepository;
    private final BillingRepository billingRepository;
    private final AuditRepository auditRepository;
    private final SettingsRepository settingsRepository;
    private final ReportRepository reportRepository;

    private final AuditService auditService;
    private final SettingsService settingsService;
    private final AuthService authService;
    private final UserService userService;
    private final TemplateService templateService;
    private final Icd10Service icd10Service;
    private final DrugCatalog drugCatalog;
    private final PdfService pdfService;
    private final PatientService patientService;
    private final SchedulingService schedulingService;
    private final EncounterService encounterService;
    private final PrescriptionService prescriptionService;
    private final LabService labService;
    private final DocumentService documentService;
    private final BillingService billingService;
    private final ReportService reportService;
    private final BackupService backupService;

    private AppContext(Database database) {
        this.database = database;
        this.userRepository = new UserRepository(database);
        this.patientRepository = new PatientRepository(database);
        this.clinicalRepository = new ClinicalRepository(database);
        this.appointmentRepository = new AppointmentRepository(database);
        this.encounterRepository = new EncounterRepository(database);
        this.prescriptionRepository = new PrescriptionRepository(database);
        this.labRepository = new LabRepository(database);
        this.documentRepository = new DocumentRepository(database);
        this.billingRepository = new BillingRepository(database);
        this.auditRepository = new AuditRepository(database);
        this.settingsRepository = new SettingsRepository(database);
        this.reportRepository = new ReportRepository(database);

        this.auditService = new AuditService(auditRepository, session);
        this.settingsService = new SettingsService(settingsRepository, session, auditService);
        this.authService = new AuthService(userRepository, session, auditService, settingsRepository);
        this.userService = new UserService(userRepository, session, auditService);
        this.templateService = new TemplateService();
        this.icd10Service = new Icd10Service(database);
        this.drugCatalog = new DrugCatalog();
        this.pdfService = new PdfService(settingsService, database);
        this.patientService = new PatientService(patientRepository, clinicalRepository, session, auditService);
        this.schedulingService = new SchedulingService(appointmentRepository, patientRepository, userRepository,
                session, auditService);
        this.encounterService = new EncounterService(encounterRepository, clinicalRepository, session,
                auditService, templateService);
        this.prescriptionService = new PrescriptionService(prescriptionRepository, clinicalRepository, session,
                auditService);
        this.labService = new LabService(labRepository, session, auditService);
        this.documentService = new DocumentService(documentRepository, database, session, auditService);
        this.billingService = new BillingService(billingRepository, patientRepository, session, auditService,
                settingsService, pdfService);
        this.reportService = new ReportService(reportRepository, billingRepository, pdfService, documentService,
                session, auditService);
        this.backupService = new BackupService(database, settingsRepository, session, auditService);
    }

    public static synchronized AppContext initialise() {
        if (instance == null) {
            Database database = Database.openDefault();
            new MigrationRunner(database).migrate();
            instance = new AppContext(database);
            instance.backupService.automaticDailyBackupIfDue();
            log.info("JSmartClinic data directory: {}", database.dataDir());
        }
        return instance;
    }

    /** For tests: build a context against a temp database file. */
    public static AppContext forDatabase(Database database) {
        return new AppContext(database);
    }

    public static AppContext get() {
        if (instance == null) {
            throw new IllegalStateException("AppContext has not been initialised");
        }
        return instance;
    }

    public Object controller(Class<?> type) {
        try {
            Object controller = type.getDeclaredConstructor().newInstance();
            if (controller instanceof ContextAware aware) {
                aware.init(this);
            }
            return controller;
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new IllegalStateException("Unable to create controller " + type.getName()
                    + " (a public no-arg constructor is required)", e);
        }
    }

    public void shutdown() {
        database.checkpoint();
    }

    public Database database() {
        return database;
    }

    public Session session() {
        return session;
    }

    public Navigator navigator() {
        return navigator;
    }

    public AuthService auth() {
        return authService;
    }

    public UserService users() {
        return userService;
    }

    public SettingsService settings() {
        return settingsService;
    }

    public PatientService patients() {
        return patientService;
    }

    public SchedulingService scheduling() {
        return schedulingService;
    }

    public EncounterService encounters() {
        return encounterService;
    }

    public PrescriptionService prescriptions() {
        return prescriptionService;
    }

    public LabService labs() {
        return labService;
    }

    public DocumentService documents() {
        return documentService;
    }

    public BillingService billing() {
        return billingService;
    }

    public ReportService reports() {
        return reportService;
    }

    public BackupService backups() {
        return backupService;
    }

    public TemplateService templates() {
        return templateService;
    }

    public Icd10Service icd10() {
        return icd10Service;
    }

    public DrugCatalog drugs() {
        return drugCatalog;
    }

    public AuditService audit() {
        return auditService;
    }

    /** Audit trail queries (same service as {@link #audit()}). */
    public AuditService auditEvents() {
        return auditService;
    }

    public PdfService pdf() {
        return pdfService;
    }

    public SettingsRepository settingsRepository() {
        return settingsRepository;
    }

    public PatientRepository patientRepository() {
        return patientRepository;
    }
}
