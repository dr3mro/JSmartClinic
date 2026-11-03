package org.amrosman.jsmartclinic.ui.patients;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.app.ContextAware;
import org.amrosman.jsmartclinic.app.Navigator;
import org.amrosman.jsmartclinic.domain.Allergy;
import org.amrosman.jsmartclinic.domain.BillableService;
import org.amrosman.jsmartclinic.domain.Encounter;
import org.amrosman.jsmartclinic.domain.Invoice;
import org.amrosman.jsmartclinic.domain.InvoiceStatus;
import org.amrosman.jsmartclinic.domain.LabOrderStatus;
import org.amrosman.jsmartclinic.domain.LabTest;
import org.amrosman.jsmartclinic.domain.Medication;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.PatientDocument;
import org.amrosman.jsmartclinic.domain.Prescription;
import org.amrosman.jsmartclinic.domain.PrescriptionItem;
import org.amrosman.jsmartclinic.domain.Problem;
import org.amrosman.jsmartclinic.domain.Vital;
import org.amrosman.jsmartclinic.repository.LabRepository;
import org.amrosman.jsmartclinic.service.Permission;
import org.amrosman.jsmartclinic.ui.billing.InvoiceDialog;
import org.amrosman.jsmartclinic.ui.billing.PaymentDialog;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Dialogs;
import org.amrosman.jsmartclinic.ui.components.Toasts;
import org.amrosman.jsmartclinic.ui.components.Ui;
import org.amrosman.jsmartclinic.ui.labs.LabOrderDialog;
import org.amrosman.jsmartclinic.ui.labs.LabResultDialog;
import org.amrosman.jsmartclinic.ui.prescriptions.PrescriptionDialog;
import org.amrosman.jsmartclinic.util.Dates;
import org.amrosman.jsmartclinic.util.Money;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Tabbed patient chart covering the full clinical record. */
public class PatientChartController implements ContextAware, Navigator.ParamAware, Navigator.Refreshable {

    // Header
    @javafx.fxml.FXML private TabPane chartTabs;
    @javafx.fxml.FXML private Label avatarLabel;
    @javafx.fxml.FXML private ImageView photoView;
    @javafx.fxml.FXML private Label patientName;
    @javafx.fxml.FXML private Label patientMeta;
    @javafx.fxml.FXML private Label permissionHint;
    @javafx.fxml.FXML private Label allergyBanner;
    @javafx.fxml.FXML private Button editButton;
    @javafx.fxml.FXML private Button encounterButton;
    @javafx.fxml.FXML private Button prescriptionButton;
    @javafx.fxml.FXML private Button labButton;
    @javafx.fxml.FXML private Button invoiceButton;

    // Summary
    @javafx.fxml.FXML private GridPane demographicsGrid;
    @javafx.fxml.FXML private HBox summaryVitals;
    @javafx.fxml.FXML private Label problemSummaryLabel;
    @javafx.fxml.FXML private Label medicationSummaryLabel;
    @javafx.fxml.FXML private Label balanceLabel;
    @javafx.fxml.FXML private TableView<Encounter> summaryEncounters;
    @javafx.fxml.FXML private TableColumn<Encounter, String> summaryEncounterDate;
    @javafx.fxml.FXML private TableColumn<Encounter, String> summaryEncounterSpecialty;
    @javafx.fxml.FXML private TableColumn<Encounter, String> summaryEncounterStatus;

    // Encounters
    @javafx.fxml.FXML private TableView<Encounter> encountersTable;
    @javafx.fxml.FXML private TableColumn<Encounter, String> encounterDate;
    @javafx.fxml.FXML private TableColumn<Encounter, String> encounterSpecialty;
    @javafx.fxml.FXML private TableColumn<Encounter, String> encounterTemplate;
    @javafx.fxml.FXML private TableColumn<Encounter, String> encounterStatus;
    @javafx.fxml.FXML private TableColumn<Encounter, String> encounterSigned;

    // Problems
    @javafx.fxml.FXML private TableView<Problem> problemsTable;
    @javafx.fxml.FXML private TableColumn<Problem, String> problemCode;
    @javafx.fxml.FXML private TableColumn<Problem, String> problemDescription;
    @javafx.fxml.FXML private TableColumn<Problem, String> problemOnset;
    @javafx.fxml.FXML private TableColumn<Problem, String> problemStatus;

    // Allergies
    @javafx.fxml.FXML private TableView<Allergy> allergiesTable;
    @javafx.fxml.FXML private TableColumn<Allergy, String> allergySubstance;
    @javafx.fxml.FXML private TableColumn<Allergy, String> allergyReaction;
    @javafx.fxml.FXML private TableColumn<Allergy, String> allergySeverity;
    @javafx.fxml.FXML private TableColumn<Allergy, String> allergyActive;

    // Medications
    @javafx.fxml.FXML private TableView<Medication> medicationsTable;
    @javafx.fxml.FXML private TableColumn<Medication, String> medName;
    @javafx.fxml.FXML private TableColumn<Medication, String> medDose;
    @javafx.fxml.FXML private TableColumn<Medication, String> medRoute;
    @javafx.fxml.FXML private TableColumn<Medication, String> medFrequency;
    @javafx.fxml.FXML private TableColumn<Medication, String> medStart;
    @javafx.fxml.FXML private TableColumn<Medication, String> medActive;

    // Vitals
    @javafx.fxml.FXML private TableView<Vital> vitalsTable;
    @javafx.fxml.FXML private TableColumn<Vital, String> vitalMeasured;
    @javafx.fxml.FXML private TableColumn<Vital, String> vitalBp;
    @javafx.fxml.FXML private TableColumn<Vital, String> vitalHr;
    @javafx.fxml.FXML private TableColumn<Vital, String> vitalTemp;
    @javafx.fxml.FXML private TableColumn<Vital, String> vitalSpo2;
    @javafx.fxml.FXML private TableColumn<Vital, String> vitalWeight;
    @javafx.fxml.FXML private TableColumn<Vital, String> vitalBmi;

    // Labs
    @javafx.fxml.FXML private TableView<LabRepository.LabRow> labsTable;
    @javafx.fxml.FXML private TableColumn<LabRepository.LabRow, String> labOrdered;
    @javafx.fxml.FXML private TableColumn<LabRepository.LabRow, String> labTest;
    @javafx.fxml.FXML private TableColumn<LabRepository.LabRow, String> labValue;
    @javafx.fxml.FXML private TableColumn<LabRepository.LabRow, String> labUnit;
    @javafx.fxml.FXML private TableColumn<LabRepository.LabRow, String> labRange;
    @javafx.fxml.FXML private TableColumn<LabRepository.LabRow, String> labFlag;
    @javafx.fxml.FXML private TableColumn<LabRepository.LabRow, String> labStatus;

    // Prescriptions
    @javafx.fxml.FXML private TableView<Prescription> prescriptionsTable;
    @javafx.fxml.FXML private TableColumn<Prescription, String> rxIssued;
    @javafx.fxml.FXML private TableColumn<Prescription, String> rxPrescriber;
    @javafx.fxml.FXML private TableColumn<Prescription, String> rxItems;
    @javafx.fxml.FXML private TableColumn<Prescription, String> rxNotes;

    // Documents
    @javafx.fxml.FXML private TableView<PatientDocument> documentsTable;
    @javafx.fxml.FXML private TableColumn<PatientDocument, String> docTitle;
    @javafx.fxml.FXML private TableColumn<PatientDocument, String> docFile;
    @javafx.fxml.FXML private TableColumn<PatientDocument, String> docType;
    @javafx.fxml.FXML private TableColumn<PatientDocument, String> docUploaded;

    // Billing
    @javafx.fxml.FXML private TableView<Invoice> invoicesTable;
    @javafx.fxml.FXML private TableColumn<Invoice, String> invoiceNo;
    @javafx.fxml.FXML private TableColumn<Invoice, String> invoiceIssued;
    @javafx.fxml.FXML private TableColumn<Invoice, String> invoiceTotal;
    @javafx.fxml.FXML private TableColumn<Invoice, String> invoiceBalance;
    @javafx.fxml.FXML private TableColumn<Invoice, String> invoiceStatus;
    @javafx.fxml.FXML private Label billingBalance;

    private AppContext context;
    private long patientId;
    private Patient patient;
    private PatientDocument selectedDocument;
    private final Map<Long, String> prescriberNames = new HashMap<>();
    private final Map<Long, Integer> prescriptionItemCounts = new HashMap<>();
    private final Map<Long, Long> invoicePaidTotals = new HashMap<>();
    private String currency = "$";

    @Override
    public void init(AppContext context) {
        this.context = context;
    }

    @javafx.fxml.FXML
    private void initialize() {
        if (context == null) {
            context = AppContext.get();
        }
        setupTables();
        applyPermissions();
    }

    private void setupTables() {
        encountersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column(encounterDate, e -> Dates.display(e.encounterDate()));
        column(encounterSpecialty, e -> nz(e.specialty()));
        column(encounterTemplate, e -> nz(e.templateId()));
        column(encounterStatus, e -> e.status().name());
        encounterStatus.setCellFactory(Ui.badgeCell());
        column(encounterSigned, e -> e.signedAt() == null ? "—" : Dates.display(e.signedAt()));
        encountersTable.setPlaceholder(Ui.emptyState("No encounters", "Start a new encounter to document this visit"));
        encountersTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                onOpenEncounter();
            }
        });

        column(summaryEncounterDate, e -> Dates.display(e.encounterDate()));
        column(summaryEncounterSpecialty, e -> nz(e.specialty()));
        column(summaryEncounterStatus, e -> e.status().name());
        summaryEncounterStatus.setCellFactory(Ui.badgeCell());

        problemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column(problemCode, p -> nz(p.icd10Code()));
        column(problemDescription, Problem::description);
        column(problemOnset, p -> Dates.displayDate(p.onsetDate()));
        column(problemStatus, p -> p.status().name());
        problemStatus.setCellFactory(Ui.badgeCell());
        problemsTable.setPlaceholder(Ui.emptyState("No problems recorded", "Add chronic conditions and diagnoses"));

        allergiesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column(allergySubstance, Allergy::substance);
        column(allergyReaction, a -> nz(a.reaction()));
        column(allergySeverity, a -> a.severity() == null ? "—" : a.severity().name());
        allergySeverity.setCellFactory(Ui.badgeCell());
        column(allergyActive, a -> a.active() ? "Active" : "Inactive");
        allergiesTable.setPlaceholder(Ui.emptyState("No known allergies", "Record any drug or food allergies"));

        medicationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column(medName, Medication::name);
        column(medDose, m -> nz(m.dose()));
        column(medRoute, m -> nz(m.route()));
        column(medFrequency, m -> nz(m.frequency()));
        column(medStart, m -> Dates.displayDate(m.startDate()));
        column(medActive, m -> m.active() ? "Active" : "Stopped");
        medicationsTable.setPlaceholder(Ui.emptyState("No medications", "Add the patient's current medication list"));

        vitalsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column(vitalMeasured, v -> Dates.display(v.measuredAt()));
        column(vitalBp, Vital::bloodPressure);
        column(vitalHr, v -> v.heartRate() == null ? "—" : String.valueOf(v.heartRate()));
        column(vitalTemp, v -> v.temperatureC() == null ? "—" : String.format(java.util.Locale.US, "%.1f", v.temperatureC()));
        column(vitalSpo2, v -> v.spo2() == null ? "—" : v.spo2() + "%");
        column(vitalWeight, v -> v.weightKg() == null ? "—" : String.format(java.util.Locale.US, "%.1f kg", v.weightKg()));
        column(vitalBmi, v -> v.bmi() == null ? "—" : String.format(java.util.Locale.US, "%.1f", v.bmi()));
        vitalsTable.setPlaceholder(Ui.emptyState("No vitals", "Record height, weight, blood pressure and more"));

        labsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column(labOrdered, row -> Dates.display(row.orderedAt()));
        column(labTest, LabRepository.LabRow::testName);
        column(labValue, row -> row.valueNum() != null ? trim(row.valueNum())
                : row.valueText() == null ? "Pending" : row.valueText());
        column(labUnit, row -> nz(row.unit()));
        column(labRange, LabResultDialog::reference);
        column(labFlag, row -> row.flag() == null ? "—" : row.flag().name());
        labFlag.setCellFactory(Ui.badgeCell());
        column(labStatus, row -> row.orderStatus().name());
        labStatus.setCellFactory(Ui.badgeCell());
        labsTable.setPlaceholder(Ui.emptyState("No lab orders", "Order tests from this chart"));

        prescriptionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column(rxIssued, p -> Dates.display(p.issuedAt()));
        column(rxPrescriber, p -> prescriberNames.getOrDefault(p.prescriberId(), "—"));
        column(rxItems, p -> String.valueOf(prescriptionItemCounts.getOrDefault(p.id(), 0)));
        column(rxNotes, p -> nz(p.notes()));
        prescriptionsTable.setPlaceholder(Ui.emptyState("No prescriptions", "Issue the first prescription"));

        documentsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column(docTitle, PatientDocument::title);
        column(docFile, PatientDocument::fileName);
        column(docType, d -> nz(d.mimeType()));
        column(docUploaded, d -> Dates.display(d.createdAt()));
        documentsTable.setPlaceholder(Ui.emptyState("No documents", "Attach referral letters, scans or reports"));
        documentsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, value) ->
                selectedDocument = value);
        documentsTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                onOpenDocument();
            }
        });

        invoicesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column(invoiceNo, Invoice::invoiceNo);
        column(invoiceIssued, i -> Dates.display(i.issuedAt()));
        column(invoiceTotal, i -> Money.format(i.totalCents()));
        column(invoiceBalance, i -> Money.format(i.totalCents() - invoicePaidTotals.getOrDefault(i.id(), 0L)));
        column(invoiceStatus, i -> i.status().name());
        invoiceStatus.setCellFactory(Ui.badgeCell());
        invoicesTable.setPlaceholder(Ui.emptyState("No invoices", "Create an invoice for this patient"));
        invoicesTable.getSelectionModel().selectedItemProperty().addListener((obs, old, value) ->
                updateInvoiceBalance(value));
    }

    private void applyPermissions() {
        boolean canEdit = context.session().can(Permission.PATIENT_EDIT);
        boolean canEncounter = context.session().can(Permission.ENCOUNTERS);
        boolean canPrescribe = context.session().can(Permission.PRESCRIBE);
        boolean canLab = context.session().can(Permission.LABS);
        boolean canBill = context.session().can(Permission.BILLING);
        editButton.setVisible(canEdit);
        editButton.setManaged(canEdit);
        encounterButton.setVisible(canEncounter);
        encounterButton.setManaged(canEncounter);
        prescriptionButton.setVisible(canPrescribe);
        prescriptionButton.setManaged(canPrescribe);
        labButton.setVisible(canLab);
        labButton.setManaged(canLab);
        invoiceButton.setVisible(canBill);
        invoiceButton.setManaged(canBill);
        billingBalance.setVisible(canBill);
        billingBalance.setManaged(canBill);
        boolean restricted = !canEdit || !canEncounter || !canPrescribe || !canLab || !canBill;
        permissionHint.setText(restricted
                ? "Some actions are hidden because your role does not allow them."
                : "");
        permissionHint.setVisible(restricted);
        permissionHint.setManaged(restricted);
    }

    @Override
    public void setParam(Object param) {
        if (param instanceof Number number) {
            patientId = number.longValue();
        }
        load();
    }

    @Override
    public void refresh() {
        if (patientId > 0) {
            load();
        }
    }

    private void load() {
        if (patientId <= 0) {
            return;
        }
        long id = patientId;
        boolean billing = context.session().can(Permission.BILLING);
        Async.run(() -> {
            Patient chart = context.patients().chart(id);
            if (chart == null) {
                throw new IllegalArgumentException("Patient not found");
            }
            List<Encounter> encounters = context.encounters().forPatient(id);
            List<Problem> problems = context.patients().problems(id);
            List<Allergy> allergies = context.patients().allergies(id);
            List<Medication> medications = context.patients().medications(id);
            List<Vital> vitals = context.patients().vitals(id, 100);
            List<LabRepository.LabRow> labs = context.labs().forPatient(id);
            List<Prescription> prescriptions = context.prescriptions().forPatient(id);
            List<PatientDocument> documents = context.documents().forPatient(id);
            List<Invoice> invoices = billing ? context.billing().invoicesForPatient(id) : List.of();
            long balance = billing ? context.billing().balance(id) : 0;
            Map<Long, Long> paidTotals = new HashMap<>();
            for (Invoice invoice : invoices) {
                paidTotals.put(invoice.id(), context.billing().paidTotal(invoice.id()));
            }
            String currency = context.settings().currency();
            Map<Long, String> prescribers = new HashMap<>();
            Map<Long, Integer> counts = new HashMap<>();
            for (Prescription prescription : prescriptions) {
                var user = context.users().byId(prescription.prescriberId());
                prescribers.put(prescription.prescriberId(), user == null ? "—" : user.fullName());
                counts.put(prescription.id(), context.prescriptions().items(prescription.id()).size());
            }
            return new ChartData(chart, encounters, problems, allergies, medications, vitals, labs,
                    prescriptions, documents, invoices, balance, prescribers, counts, paidTotals, currency);
        }, this::apply, Dialogs::error);
    }

    private void apply(ChartData data) {
        patient = data.patient();
        prescriberNames.clear();
        prescriberNames.putAll(data.prescribers());
        prescriptionItemCounts.clear();
        prescriptionItemCounts.putAll(data.itemCounts());
        invoicePaidTotals.clear();
        invoicePaidTotals.putAll(data.invoicePaidTotals());
        currency = data.currency();
        invoicesTable.refresh();

        patientName.setText(patient.fullName().trim());
        patientMeta.setText(patient.mrn() + "   ·   " + patient.age() + " years   ·   "
                + (patient.sex() == null ? "Unknown" : patient.sex().label()) + "   ·   born "
                + Dates.displayDate(patient.dateOfBirth())
                + (patient.phone() == null ? "" : "   ·   " + patient.phone()));
        avatarLabel.setText(patient.initials());
        if (patient.photo() != null && patient.photo().length > 0) {
            photoView.setImage(new Image(new ByteArrayInputStream(patient.photo())));
            photoView.setVisible(true);
            avatarLabel.setVisible(false);
        } else {
            photoView.setImage(null);
            photoView.setVisible(false);
            avatarLabel.setVisible(true);
        }
        String allergyText = data.allergies().isEmpty() ? null
                : "Allergies: " + data.allergies().stream().map(Allergy::substance)
                .reduce((a, b) -> a + ", " + b).orElse("");
        allergyBanner.setText(allergyText == null ? "" : allergyText);
        allergyBanner.setVisible(allergyText != null);
        allergyBanner.setManaged(allergyText != null);

        summaryEncounters.getItems().setAll(data.encounters().stream().limit(8).toList());
        encountersTable.getItems().setAll(data.encounters());
        problemsTable.getItems().setAll(data.problems());
        allergiesTable.getItems().setAll(data.allergies());
        medicationsTable.getItems().setAll(data.medications());
        vitalsTable.getItems().setAll(data.vitals());
        labsTable.getItems().setAll(data.labs());
        prescriptionsTable.getItems().setAll(data.prescriptions());
        documentsTable.getItems().setAll(data.documents());
        invoicesTable.getItems().setAll(data.invoices());

        problemSummaryLabel.setText(data.problems().stream().filter(p -> p.status().name().equals("ACTIVE"))
                .map(Problem::description).reduce((a, b) -> a + ", " + b).orElse("None recorded"));
        medicationSummaryLabel.setText(data.medications().stream().filter(Medication::active)
                .map(m -> m.name() + (m.dose() == null ? "" : " " + m.dose()))
                .reduce((a, b) -> a + ", " + b).orElse("None recorded"));
        balanceLabel.setText(Money.format(data.balance(), currency));
        billingBalance.setText("Balance  " + Money.format(data.balance(), currency));
        fillDemographics(patient);
        fillSummaryVitals(data.vitals().isEmpty() ? null : data.vitals().get(0));
    }

    private void fillDemographics(Patient p) {
        demographicsGrid.getChildren().clear();
        int row = 0;
        row = info(row, "Phone", nz(p.phone()));
        row = info(row, "Email", nz(p.email()));
        row = info(row, "Address", nz(join(p.address(), p.city())));
        row = info(row, "National ID", nz(p.nationalId()));
        row = info(row, "Blood type", nz(p.bloodType()));
        row = info(row, "Marital status", nz(p.maritalStatus()));
        row = info(row, "Occupation", nz(p.occupation()));
        row = info(row, "Emergency contact", nz(join(p.emergencyName(), p.emergencyPhone())));
        info(row, "Insurance", nz(join(p.insuranceProvider(), p.insuranceNumber())));
    }

    private int info(int row, String label, String value) {
        Label caption = new Label(label);
        caption.getStyleClass().add("section-label");
        Label content = new Label(value);
        content.setWrapText(true);
        demographicsGrid.add(caption, 0, row);
        demographicsGrid.add(content, 1, row);
        return row + 1;
    }

    private void fillSummaryVitals(Vital vital) {
        summaryVitals.getChildren().clear();
        if (vital == null) {
            summaryVitals.getChildren().add(Ui.muted("No vitals recorded"));
            return;
        }
        summaryVitals.getChildren().addAll(
                vitalTile("Blood pressure", vital.bloodPressure()),
                vitalTile("Heart rate", vital.heartRate() == null ? "—" : vital.heartRate() + " bpm"),
                vitalTile("SpO₂", vital.spo2() == null ? "—" : vital.spo2() + "%"),
                vitalTile("BMI", vital.bmi() == null ? "—" : String.valueOf(vital.bmi())));
    }

    private VBox vitalTile(String caption, String value) {
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("vital-caption");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("vital-value");
        VBox tile = new VBox(2, captionLabel, valueLabel);
        tile.getStyleClass().add("vital-tile");
        HBox.setHgrow(tile, javafx.scene.layout.Priority.ALWAYS);
        return tile;
    }

    @javafx.fxml.FXML
    private void onBack() {
        context.navigator().show("patients");
    }

    @javafx.fxml.FXML
    private void onEdit() {
        PatientDialog.open(context, patient, saved -> load());
    }

    @javafx.fxml.FXML
    private void onNewEncounter() {
        Async.run(() -> context.encounters().start(patientId, null, null, null), encounter -> {
            Toasts.success("Encounter started");
            context.navigator().show("encounter-editor", encounter.id());
        });
    }

    @javafx.fxml.FXML
    private void onNewPrescription() {
        PrescriptionDialog.open(context, patientId, null, id -> {
            Toasts.success("Prescription issued");
            load();
        });
    }

    @javafx.fxml.FXML
    private void onOrderLabs() {
        LabOrderDialog.open(context, patientId, null, id -> {
            Toasts.success("Lab order placed");
            load();
        });
    }

    @javafx.fxml.FXML
    private void onNewInvoice() {
        Async.run(() -> context.billing().services(true), services -> createInvoice(services), Dialogs::error);
    }

    private void createInvoice(List<BillableService> services) {
        InvoiceDialog.open(context, patientId, null, services, id -> {
            Toasts.success("Invoice created");
            load();
        });
    }

    @javafx.fxml.FXML
    private void onOpenEncounter() {
        Encounter selected = encountersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            selected = summaryEncounters.getSelectionModel().getSelectedItem();
        }
        if (selected != null) {
            context.navigator().show("encounter-editor", selected.id());
        }
    }

    @javafx.fxml.FXML
    private void onDeleteDraft() {
        Encounter selected = encountersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (selected.status().name().equals("SIGNED") || selected.status().name().equals("AMENDED")) {
            Dialogs.warn("Signed encounters cannot be deleted. Create an addendum instead.");
            return;
        }
        if (!Dialogs.confirm("Discard draft", "Delete this draft encounter?")) {
            return;
        }
        Async.run(() -> {
            context.encounters().deleteDraft(selected.id());
            return null;
        }, ignored -> load());
    }

    @javafx.fxml.FXML
    private void onAddProblem() {
        ProblemDialog.open(context, patientId, null, ignored -> load());
    }

    @javafx.fxml.FXML
    private void onEditProblem() {
        Problem selected = problemsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            ProblemDialog.open(context, patientId, selected, ignored -> load());
        }
    }

    @javafx.fxml.FXML
    private void onRemoveProblem() {
        Problem selected = problemsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (Dialogs.confirm("Remove problem", "Remove \"" + selected.description() + "\" from the problem list?")) {
            Async.run(() -> {
                context.patients().removeProblem(selected.id(), patientId);
                return null;
            }, ignored -> load());
        }
    }

    @javafx.fxml.FXML
    private void onAddAllergy() {
        AllergyDialog.open(context, patientId, ignored -> load());
    }

    @javafx.fxml.FXML
    private void onRemoveAllergy() {
        Allergy selected = allergiesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (Dialogs.confirm("Remove allergy", "Remove " + selected.substance() + " from the allergy list?")) {
            Async.run(() -> {
                context.patients().removeAllergy(selected.id(), patientId);
                return null;
            }, ignored -> load());
        }
    }

    @javafx.fxml.FXML
    private void onAddMedication() {
        MedicationDialog.open(context, patientId, null, ignored -> load());
    }

    @javafx.fxml.FXML
    private void onEditMedication() {
        Medication selected = medicationsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            MedicationDialog.open(context, patientId, selected, ignored -> load());
        }
    }

    @javafx.fxml.FXML
    private void onStopMedication() {
        Medication selected = medicationsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (Dialogs.confirm("Stop medication", "Stop " + selected.name() + "?")) {
            Async.run(() -> {
                context.patients().deactivateMedication(selected.id(), patientId);
                return null;
            }, ignored -> load());
        }
    }

    @javafx.fxml.FXML
    private void onAddVital() {
        VitalDialog.open(context, patientId, null, ignored -> load());
    }

    @javafx.fxml.FXML
    private void onRemoveVital() {
        Vital selected = vitalsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (Dialogs.confirm("Remove vitals", "Remove this vitals record?")) {
            Async.run(() -> {
                context.encounters().removeVital(selected.id(), patientId);
                return null;
            }, ignored -> load());
        }
    }

    @javafx.fxml.FXML
    private void onEnterResult() {
        LabRepository.LabRow selected = labsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Dialogs.info("Select a lab line first.");
            return;
        }
        LabResultDialog.open(context, selected, flag -> load());
    }

    @javafx.fxml.FXML
    private void onLabTrend() {
        Map<String, Long> tests = new LinkedHashMap<>();
        for (LabRepository.LabRow row : labsTable.getItems()) {
            if (row.valueNum() != null) {
                tests.putIfAbsent(row.testName(), row.testId());
            }
        }
        if (tests.isEmpty()) {
            Dialogs.info("No numeric lab results available for a trend chart.");
            return;
        }
        ChoiceDialog<String> choice = new ChoiceDialog<>(tests.keySet().iterator().next(), tests.keySet());
        choice.setTitle("Lab trend");
        choice.setHeaderText("Choose a test to chart");
        org.amrosman.jsmartclinic.ui.ThemeManager.applyToDialog(choice.getDialogPane());
        Optional<String> selected = choice.showAndWait();
        if (selected.isPresent()) {
            long testId = tests.get(selected.get());
            Async.run(() -> context.labs().trend(patientId, testId), rows -> showTrend(selected.get(), rows));
        }
    }

    private void showTrend(String name, List<LabRepository.LabRow> rows) {
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(name);
        javafx.scene.chart.CategoryAxis xAxis = new javafx.scene.chart.CategoryAxis();
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(name + " trend");
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(name);
        for (LabRepository.LabRow row : rows) {
            series.getData().add(new XYChart.Data<>(Dates.displayDate(row.orderedAt()),
                    row.valueNum() == null ? 0 : row.valueNum()));
        }
        chart.getData().add(series);
        chart.setPrefSize(640, 400);
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Lab trend");
        dialog.getDialogPane().setContent(chart);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        org.amrosman.jsmartclinic.ui.ThemeManager.applyToDialog(dialog.getDialogPane());
        dialog.showAndWait();
    }

    @javafx.fxml.FXML
    private void onViewPrescription() {
        Prescription selected = prescriptionsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            PrescriptionDialog.view(context, selected);
        }
    }

    @javafx.fxml.FXML
    private void onPrintPrescription() {
        Prescription selected = prescriptionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Async.run(() -> {
            List<PrescriptionItem> items = context.prescriptions().items(selected.id());
            var path = context.pdf().prescription(patient, context.session().current(), selected, items);
            context.audit().log(org.amrosman.jsmartclinic.domain.AuditAction.PRINT, "prescription",
                    selected.id(), patientId, "Prescription PDF");
            return path;
        }, path -> Dialogs.openFile(path));
    }

    @javafx.fxml.FXML
    private void onRenewPrescription() {
        Prescription selected = prescriptionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Async.run(() -> context.prescriptions().renew(selected.id()), id -> {
            Toasts.success("Prescription renewed");
            load();
        });
    }

    @javafx.fxml.FXML
    private void onAttachDocument() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Attach document");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documents",
                "*.pdf", "*.png", "*.jpg", "*.jpeg", "*.doc", "*.docx", "*.txt"));
        File file = chooser.showOpenDialog(null);
        if (file == null) {
            return;
        }
        Async.run(() -> context.documents().attach(patientId, null, file, file.getName()), document -> {
            Toasts.success("Document attached");
            load();
        });
    }

    @javafx.fxml.FXML
    private void onOpenDocument() {
        PatientDocument document = selectedDocument;
        if (document == null) {
            document = documentsTable.getSelectionModel().getSelectedItem();
        }
        if (document == null) {
            return;
        }
        PatientDocument target = document;
        Async.run(() -> context.documents().fileFor(target), path -> {
            String mime = target.mimeType() == null ? "" : target.mimeType();
            if (mime.startsWith("image/")) {
                Image image = new Image(path.toUri().toString());
                ImageView view = new ImageView(image);
                view.setPreserveRatio(true);
                view.setFitWidth(760);
                view.setFitHeight(560);
                Dialog<Void> dialog = new Dialog<>();
                dialog.setTitle(target.title());
                dialog.getDialogPane().setContent(new StackPane(view));
                dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                org.amrosman.jsmartclinic.ui.ThemeManager.applyToDialog(dialog.getDialogPane());
                dialog.showAndWait();
            } else {
                Dialogs.openFile(path);
            }
        });
    }

    @javafx.fxml.FXML
    private void onDeleteDocument() {
        PatientDocument selected = documentsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (Dialogs.confirm("Delete document", "Remove \"" + selected.title() + "\" from this chart?")) {
            Async.run(() -> {
                context.documents().delete(selected);
                return null;
            }, ignored -> load());
        }
    }

    @javafx.fxml.FXML
    private void onRecordPayment() {
        Invoice selected = invoicesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Dialogs.info("Select an invoice first.");
            return;
        }
        Async.run(() -> context.billing().paidTotal(selected.id()),
                paid -> PaymentDialog.open(context, selected, paid, amount -> {
                    Toasts.success("Payment recorded");
                    load();
                }));
    }

    @javafx.fxml.FXML
    private void onReceipt() {
        Invoice selected = invoicesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Async.run(() -> context.billing().receiptPdf(selected.id()), path -> Dialogs.openFile(path));
    }

    private void updateInvoiceBalance(Invoice invoice) {
        if (invoice == null || !context.session().can(Permission.BILLING)) {
            return;
        }
        Async.run(() -> context.billing().paidTotal(invoice.id()), paid -> {
            invoicePaidTotals.put(invoice.id(), paid);
            invoicesTable.refresh();
            billingBalance.setText("Balance  " + Money.format(invoice.totalCents() - paid, currency));
        });
    }

    private static <T> void column(TableColumn<T, String> column, java.util.function.Function<T, String> value) {
        column.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue())));
    }

    private static String nz(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String join(String first, String second) {
        if ((first == null || first.isBlank()) && (second == null || second.isBlank())) {
            return "";
        }
        return (first == null ? "" : first) + (second == null || second.isBlank() ? ""
                : (first == null || first.isBlank() ? "" : ", ") + second);
    }

    private static String trim(Double value) {
        if (value == null) {
            return "—";
        }
        return value == Math.rint(value) ? String.valueOf(value.longValue()) : String.valueOf(value);
    }

    private record ChartData(Patient patient, List<Encounter> encounters, List<Problem> problems,
                             List<Allergy> allergies, List<Medication> medications, List<Vital> vitals,
                             List<LabRepository.LabRow> labs, List<Prescription> prescriptions,
                             List<PatientDocument> documents, List<Invoice> invoices, long balance,
                             Map<Long, String> prescribers, Map<Long, Integer> itemCounts,
                             Map<Long, Long> invoicePaidTotals, String currency) {
    }
}
