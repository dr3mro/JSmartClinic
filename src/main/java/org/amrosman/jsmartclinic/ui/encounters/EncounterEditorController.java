package org.amrosman.jsmartclinic.ui.encounters;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.app.ContextAware;
import org.amrosman.jsmartclinic.app.Navigator;
import org.amrosman.jsmartclinic.domain.Encounter;
import org.amrosman.jsmartclinic.domain.EncounterDiagnosis;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.Vital;
import org.amrosman.jsmartclinic.service.Permission;
import org.amrosman.jsmartclinic.service.TemplateService;
import org.amrosman.jsmartclinic.ui.billing.InvoiceDialog;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Dialogs;
import org.amrosman.jsmartclinic.ui.components.Icd10Picker;
import org.amrosman.jsmartclinic.ui.components.Toasts;
import org.amrosman.jsmartclinic.ui.components.Ui;
import org.amrosman.jsmartclinic.ui.patients.VitalDialog;
import org.amrosman.jsmartclinic.util.Dates;
import org.amrosman.jsmartclinic.util.Json;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SOAP encounter editor with specialty templates, diagnoses, vitals and autosave. */
public class EncounterEditorController implements ContextAware, Navigator.ParamAware {

    @FXML private Label avatarLabel;
    @FXML private Label patientName;
    @FXML private Label patientMeta;
    @FXML private Label encounterDateLabel;
    @FXML private Label statusLabel;
    @FXML private Label signedBanner;
    @FXML private Label autosaveLabel;
    @FXML private TextField chiefComplaint;
    @FXML private TextArea subjective;
    @FXML private TextArea objective;
    @FXML private TextArea assessment;
    @FXML private TextArea plan;
    @FXML private ComboBox<TemplateService.Template> templateCombo;
    @FXML private Label templateHint;
    @FXML private VBox templateBox;
    @FXML private TableView<EncounterDiagnosis> diagnosesTable;
    @FXML private TableColumn<EncounterDiagnosis, String> diagnosisCode;
    @FXML private TableColumn<EncounterDiagnosis, String> diagnosisDescription;
    @FXML private TableColumn<EncounterDiagnosis, String> diagnosisPrimary;
    @FXML private TableView<Vital> vitalsTable;
    @FXML private TableColumn<Vital, String> vitalMeasured;
    @FXML private TableColumn<Vital, String> vitalBp;
    @FXML private TableColumn<Vital, String> vitalHr;
    @FXML private TableColumn<Vital, String> vitalTemp;
    @FXML private TableColumn<Vital, String> vitalSpo2;
    @FXML private TableColumn<Vital, String> vitalBmi;
    @FXML private Button saveButton;
    @FXML private Button signButton;
    @FXML private Button addendumButton;
    @FXML private Button addDiagnosisButton;
    @FXML private Button removeDiagnosisButton;
    @FXML private Button addVitalButton;
    @FXML private Button invoiceButton;

    private static final KeyCombination SAVE_SHORTCUT = KeyCombination.keyCombination("Shortcut+S");

    private final Map<String, Object> templateValues = new LinkedHashMap<>();
    private final List<Control> templateControls = new ArrayList<>();
    private final Timeline autosave = new Timeline(new KeyFrame(Duration.seconds(30), event -> autoSave()));

    private AppContext context;
    private long encounterId;
    private Encounter base;
    private Patient patient;
    private boolean applying;
    private boolean dirty;
    private boolean saving;
    private boolean editable;

    @Override
    public void init(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        if (context == null) {
            context = AppContext.get();
        }
        setupTables();
        templateCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TemplateService.Template value) {
                return value == null ? "" : value.name();
            }

            @Override
            public TemplateService.Template fromString(String string) {
                return null;
            }
        });
        templateCombo.valueProperty().addListener((obs, old, value) -> {
            if (!applying && value != null) {
                markDirty();
                renderTemplate();
            }
        });
        autosave.setCycleCount(Timeline.INDEFINITE);
        autosave.play();
        autosaveLabel.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (oldScene != null) {
                oldScene.getAccelerators().remove(SAVE_SHORTCUT);
            }
            if (scene == null) {
                autosave.pause();
            } else {
                autosave.play();
                scene.getAccelerators().put(SAVE_SHORTCUT, this::onSave);
            }
        });
        boolean canBill = context.session().can(Permission.BILLING);
        invoiceButton.setVisible(canBill);
        invoiceButton.setManaged(canBill);
        invoiceButton.setDisable(true);
        markDirtyOnEdit(chiefComplaint);
        markDirtyOnEdit(subjective);
        markDirtyOnEdit(objective);
        markDirtyOnEdit(assessment);
        markDirtyOnEdit(plan);
        saveButton.setDisable(true);
        signButton.setDisable(true);
    }

    private void setupTables() {
        diagnosesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        diagnosisCode.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().icd10Code() == null ? "—" : data.getValue().icd10Code()));
        diagnosisDescription.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().description()));
        diagnosisPrimary.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().primary() ? "Yes" : "No"));
        diagnosesTable.setPlaceholder(Ui.emptyState("No diagnoses", "Add at least one ICD-10 diagnosis"));
        diagnosesTable.getSelectionModel().selectedItemProperty().addListener((obs, old, value) ->
                updateButtons());

        vitalsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        vitalMeasured.setCellValueFactory(data -> new SimpleStringProperty(Dates.display(data.getValue().measuredAt())));
        vitalBp.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().bloodPressure()));
        vitalHr.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().heartRate() == null
                ? "—" : data.getValue().heartRate() + " bpm"));
        vitalTemp.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().temperatureC() == null
                ? "—" : String.format(java.util.Locale.US, "%.1f °C", data.getValue().temperatureC())));
        vitalSpo2.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().spo2() == null
                ? "—" : data.getValue().spo2() + "%"));
        vitalBmi.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().bmi() == null
                ? "—" : String.valueOf(data.getValue().bmi())));
        vitalsTable.setPlaceholder(Ui.emptyState("No vitals", "Record vitals for this visit"));
    }

    private void markDirtyOnEdit(javafx.scene.control.TextInputControl field) {
        field.textProperty().addListener((obs, old, value) -> markDirty());
    }

    @Override
    public void setParam(Object param) {
        if (param instanceof Number number) {
            encounterId = number.longValue();
        }
        load();
    }

    private void load() {
        if (encounterId <= 0) {
            return;
        }
        long id = encounterId;
        Async.run(() -> {
            Encounter encounter = context.encounters().byId(id);
            if (encounter == null) {
                throw new IllegalArgumentException("Encounter not found");
            }
            Patient patient = context.patients().basic(encounter.patientId());
            List<EncounterDiagnosis> diagnoses = context.encounters().diagnoses(id);
            List<Vital> vitals = context.encounters().vitalsForEncounter(id);
            return new Loaded(encounter, patient, diagnoses, vitals);
        }, this::apply, Dialogs::error);
    }

    private void apply(Loaded loaded) {
        applying = true;
        base = loaded.encounter();
        patient = loaded.patient();
        editable = !base.signed() && context.session().can(Permission.ENCOUNTERS);

        avatarLabel.setText(patient == null ? "?" : patient.initials());
        patientName.setText(patient == null ? "Encounter #" + base.id() : patient.fullName().trim());
        patientMeta.setText(patient == null ? "" : patient.mrn() + "   ·   " + patient.age() + " years   ·   "
                + (patient.sex() == null ? "Unknown" : patient.sex().label()));
        encounterDateLabel.setText(Dates.display(base.encounterDate()));
        statusLabel.setText(base.status().name());
        statusLabel.getStyleClass().setAll("badge", "badge-" + Ui.css(base.status().name()));
        if (base.signed()) {
            signedBanner.setText("This encounter was signed " + Dates.display(base.signedAt())
                    + ". It is read-only; use \"Create addendum\" to document changes.");
            signedBanner.setVisible(true);
            signedBanner.setManaged(true);
        } else {
            signedBanner.setText("");
            signedBanner.setVisible(false);
            signedBanner.setManaged(false);
        }

        chiefComplaint.setText(nz(base.chiefComplaint()));
        subjective.setText(nz(base.subjective()));
        objective.setText(nz(base.objective()));
        assessment.setText(nz(base.assessment()));
        plan.setText(nz(base.plan()));
        templateValues.clear();
        templateValues.putAll(Json.readMap(base.templateData()));

        templateCombo.getItems().setAll(context.templates().all());
        TemplateService.Template template = context.templates().byId(base.templateId())
                .orElseGet(() -> context.templates().defaultFor(base.specialty()).orElse(null));
        templateCombo.getSelectionModel().select(template);
        renderTemplate();

        diagnosesTable.getItems().setAll(loaded.diagnoses());
        vitalsTable.getItems().setAll(loaded.vitals());

        setEditable(editable);
        updateButtons();
        dirty = false;
        autosaveLabel.setText(editable ? "Draft not yet saved" : "");
        applying = false;
    }

    private void setEditable(boolean value) {
        chiefComplaint.setEditable(value);
        subjective.setEditable(value);
        objective.setEditable(value);
        assessment.setEditable(value);
        plan.setEditable(value);
        templateCombo.setDisable(!value);
        for (Control control : templateControls) {
            control.setDisable(!value);
        }
        saveButton.setVisible(value);
        saveButton.setManaged(value);
        signButton.setVisible(value);
        signButton.setManaged(value);
        addendumButton.setVisible(!value && base != null && base.signed());
        addendumButton.setManaged(addendumButton.isVisible());
        boolean vital = base != null && !base.signed() && context.session().can(Permission.VITALS);
        addVitalButton.setVisible(vital);
        addVitalButton.setManaged(vital);
        addDiagnosisButton.setVisible(value);
        addDiagnosisButton.setManaged(value);
        removeDiagnosisButton.setVisible(value);
        removeDiagnosisButton.setManaged(value);
    }

    private void updateButtons() {
        saveButton.setDisable(!editable || saving);
        signButton.setDisable(!editable || saving);
        invoiceButton.setDisable(base == null || patient == null);
        removeDiagnosisButton.setDisable(diagnosesTable.getSelectionModel().getSelectedItem() == null
                || !editable);
    }

    private void renderTemplate() {
        templateBox.getChildren().clear();
        templateControls.clear();
        TemplateService.Template template = templateCombo.getSelectionModel().getSelectedItem();
        if (template == null) {
            templateHint.setText("No template selected");
            return;
        }
        templateHint.setText(template.specialty() + " · " + template.sections().size() + " sections");
        for (TemplateService.Section section : template.sections()) {
            Label title = new Label(section.title());
            title.getStyleClass().add("section-title");
            templateBox.getChildren().add(title);
            for (TemplateService.Field field : section.fields()) {
                Label label = new Label(field.label() + (field.unit() == null ? "" : " (" + field.unit() + ")"));
                label.getStyleClass().add("form-label");
                Control control = fieldControl(field);
                templateControls.add(control);
                control.setDisable(!editable);
                templateBox.getChildren().addAll(label, control);
            }
        }
    }

    private Control fieldControl(TemplateService.Field field) {
        Object value = templateValues.get(field.key());
        String text = value == null ? "" : String.valueOf(value);
        switch (field.type() == null ? "text" : field.type()) {
            case "textarea" -> {
                TextArea area = new TextArea(text);
                area.setWrapText(true);
                area.setPrefRowCount(2);
                area.getStyleClass().add("form-field");
                area.textProperty().addListener((obs, old, changed) -> {
                    store(field.key(), changed);
                    markDirty();
                });
                return area;
            }
            case "select" -> {
                ComboBox<String> combo = new ComboBox<>();
                combo.getItems().setAll(field.options());
                combo.getStyleClass().add("form-field");
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.getSelectionModel().select(text);
                combo.valueProperty().addListener((obs, old, changed) -> {
                    store(field.key(), changed);
                    markDirty();
                });
                return combo;
            }
            case "checkbox" -> {
                CheckBox box = new CheckBox();
                box.setSelected(Boolean.parseBoolean(text));
                box.selectedProperty().addListener((obs, old, changed) -> {
                    templateValues.put(field.key(), changed);
                    markDirty();
                });
                return box;
            }
            case "date" -> {
                DatePicker picker = new DatePicker(Dates.dateFromText(text));
                picker.getStyleClass().add("form-field");
                picker.setMaxWidth(Double.MAX_VALUE);
                picker.valueProperty().addListener((obs, old, changed) -> {
                    templateValues.put(field.key(), Dates.toText(changed));
                    markDirty();
                });
                return picker;
            }
            case "number" -> {
                TextField numeric = new TextField(text);
                numeric.setPromptText(field.min() == null && field.max() == null ? "Number"
                        : field.min() + " – " + field.max());
                numeric.getStyleClass().add("form-field");
                numeric.textProperty().addListener((obs, old, changed) -> {
                    store(field.key(), changed);
                    markDirty();
                });
                return numeric;
            }
            default -> {
                TextField input = new TextField(text);
                input.setPromptText(field.placeholder() == null ? field.label() : field.placeholder());
                input.getStyleClass().add("form-field");
                input.textProperty().addListener((obs, old, changed) -> {
                    store(field.key(), changed);
                    markDirty();
                });
                return input;
            }
        }
    }

    private void store(String key, String value) {
        if (value == null || value.isBlank()) {
            templateValues.remove(key);
        } else {
            templateValues.put(key, value);
        }
    }

    private void markDirty() {
        if (applying || !editable) {
            return;
        }
        dirty = true;
        autosaveLabel.setText("Unsaved changes");
    }

    private void autoSave() {
        if (!dirty || saving || !editable || base == null) {
            return;
        }
        persist(true);
    }

    @FXML
    private void onSave() {
        persist(false);
    }

    private void persist(boolean silent) {
        if (base == null || !editable) {
            return;
        }
        saving = true;
        updateButtons();
        Encounter updated = buildEncounter();
        Async.run(() -> context.encounters().save(updated), saved -> {
            base = saved;
            dirty = false;
            saving = false;
            autosaveLabel.setText((silent ? "Autosaved " : "Saved ")
                    + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            updateButtons();
            if (!silent) {
                Toasts.success("Encounter saved");
            }
        }, throwable -> {
            saving = false;
            autosaveLabel.setText("Save failed");
            updateButtons();
            if (!silent) {
                Dialogs.error(throwable);
            }
        });
    }

    private Encounter buildEncounter() {
        return new Encounter(base.id(), base.patientId(), base.providerId(), base.appointmentId(),
                base.specialty(),
                templateCombo.getSelectionModel().getSelectedItem() == null ? base.templateId()
                        : templateCombo.getSelectionModel().getSelectedItem().id(),
                base.encounterDate(), chiefComplaint.getText(), subjective.getText(), objective.getText(),
                assessment.getText(), plan.getText(), Json.write(templateValues), base.status(),
                base.signedAt(), base.signedBy(), base.createdAt(), base.updatedAt());
    }

    @FXML
    private void onSign() {
        if (base == null || !editable) {
            return;
        }
        if (!Dialogs.confirm("Sign encounter",
                "Signing makes the note read-only. Later changes require an addendum.\n\nSign now?")) {
            return;
        }
        saving = true;
        updateButtons();
        Encounter updated = buildEncounter();
        Async.run(() -> {
            context.encounters().save(updated);
            return context.encounters().sign(updated.id());
        }, signed -> {
            saving = false;
            dirty = false;
            Toasts.success("Encounter signed");
            load();
        }, throwable -> {
            saving = false;
            updateButtons();
            Dialogs.error(throwable);
        });
    }

    @FXML
    private void onAddendum() {
        if (base == null) {
            return;
        }
        if (!Dialogs.confirm("Create addendum",
                "Create an addendum linked to the signed encounter?\nThe original note is preserved.")) {
            return;
        }
        long original = base.id();
        Async.run(() -> context.encounters().amend(original), amendment -> {
            Toasts.success("Addendum created");
            context.navigator().show("encounter-editor", amendment.id());
        });
    }

    @FXML
    private void onInvoice() {
        if (base == null || patient == null) {
            return;
        }
        Async.run(() -> context.billing().services(true), services -> {
            if (services.isEmpty()) {
                Dialogs.warn("The service catalogue is empty. Add services in Settings first.");
                return;
            }
            InvoiceDialog.open(context, patient.id(), base.id(), services, id -> {
                Toasts.success("Invoice created");
                context.navigator().show("billing");
            });
        });
    }

    @FXML
    private void onAddDiagnosis() {
        if (base == null || !editable) {
            return;
        }
        Icd10Picker.choose(context, "").ifPresent(code -> Async.run(() -> {
            context.encounters().addDiagnosis(base.id(), base.patientId(), code.code(), code.description(),
                    diagnosesTable.getItems().isEmpty());
            return null;
        }, ignored -> reloadDiagnoses()));
    }

    @FXML
    private void onRemoveDiagnosis() {
        EncounterDiagnosis selected = diagnosesTable.getSelectionModel().getSelectedItem();
        if (selected == null || base == null) {
            return;
        }
        if (!Dialogs.confirm("Remove diagnosis", "Remove \"" + selected.description() + "\"?")) {
            return;
        }
        Async.run(() -> {
            context.encounters().removeDiagnosis(selected.id(), base.patientId());
            return null;
        }, ignored -> reloadDiagnoses());
    }

    private void reloadDiagnoses() {
        if (base == null) {
            return;
        }
        Async.run(() -> context.encounters().diagnoses(base.id()),
                list -> diagnosesTable.getItems().setAll(list));
    }

    @FXML
    private void onAddVitals() {
        if (base == null) {
            return;
        }
        VitalDialog.open(context, base.patientId(), base.id(), ignored -> reloadVitals());
    }

    private void reloadVitals() {
        if (base == null) {
            return;
        }
        Async.run(() -> context.encounters().vitalsForEncounter(base.id()),
                list -> vitalsTable.getItems().setAll(list));
    }

    @FXML
    private void onBack() {
        if (dirty && !Dialogs.confirm("Unsaved changes", "Discard unsaved changes and leave the editor?")) {
            return;
        }
        autosave.stop();
        if (patient != null) {
            context.navigator().show("patient-chart", patient.id());
        } else {
            context.navigator().show("encounters");
        }
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private record Loaded(Encounter encounter, Patient patient, List<EncounterDiagnosis> diagnoses,
                          List<Vital> vitals) {
    }
}
