package org.amrosman.jsmartclinic.ui.encounters;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.app.ContextAware;
import org.amrosman.jsmartclinic.app.Navigator;
import org.amrosman.jsmartclinic.domain.Encounter;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.User;
import org.amrosman.jsmartclinic.service.Permission;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Dialogs;
import org.amrosman.jsmartclinic.ui.components.Toasts;
import org.amrosman.jsmartclinic.ui.components.Ui;
import org.amrosman.jsmartclinic.ui.patients.PatientPicker;
import org.amrosman.jsmartclinic.util.Dates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Registry of recent encounters with status filtering. */
public class EncountersController implements ContextAware, Navigator.Refreshable {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Label countLabel;
    @FXML private Label draftLabel;
    @FXML private TableView<EncounterRow> encounterTable;
    @FXML private TableColumn<EncounterRow, String> dateColumn;
    @FXML private TableColumn<EncounterRow, String> patientColumn;
    @FXML private TableColumn<EncounterRow, String> mrnColumn;
    @FXML private TableColumn<EncounterRow, String> providerColumn;
    @FXML private TableColumn<EncounterRow, String> specialtyColumn;
    @FXML private TableColumn<EncounterRow, String> statusColumn;
    @FXML private TableColumn<EncounterRow, String> signedColumn;
    @FXML private Button openButton;
    @FXML private Button newButton;

    private AppContext context;
    private final List<EncounterRow> allRows = new ArrayList<>();

    @Override
    public void init(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        if (context == null) {
            context = AppContext.get();
        }
        statusFilter.getItems().setAll("All statuses", "DRAFT", "SIGNED", "AMENDED");
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.valueProperty().addListener((obs, old, value) -> applyFilter());
        searchField.textProperty().addListener((obs, old, value) -> applyFilter());

        encounterTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(Dates.display(data.getValue().encounter().encounterDate())));
        patientColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().patientName()));
        mrnColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().mrn()));
        providerColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().providerName()));
        specialtyColumn.setCellValueFactory(data -> new SimpleStringProperty(
                blank(data.getValue().encounter().specialty())));
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().encounter().status().name()));
        statusColumn.setCellFactory(Ui.badgeCell());
        signedColumn.setCellValueFactory(data -> new SimpleStringProperty(
                Dates.display(data.getValue().encounter().signedAt())));
        encounterTable.setPlaceholder(Ui.emptyState("No encounters yet", "Start an encounter from a patient chart"));
        encounterTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                onOpen();
            }
        });
        encounterTable.getSelectionModel().selectedItemProperty().addListener((obs, old, value) ->
                openButton.setDisable(value == null));

        boolean canWrite = context.session().can(Permission.ENCOUNTERS);
        newButton.setVisible(canWrite);
        newButton.setManaged(canWrite);
        openButton.setDisable(true);
        load();
    }

    @Override
    public void refresh() {
        load();
    }

    private void load() {
        countLabel.setText("Loading…");
        Async.run(() -> {
            List<Encounter> encounters = context.encounters().recent(300);
            Map<Long, Patient> patientCache = new HashMap<>();
            Map<Long, User> userCache = new HashMap<>();
            List<EncounterRow> rows = new ArrayList<>(encounters.size());
            for (Encounter encounter : encounters) {
                Patient patient = patientCache.computeIfAbsent(encounter.patientId(),
                        id -> context.patients().basic(id));
                User provider = userCache.computeIfAbsent(encounter.providerId(),
                        id -> context.users().byId(id));
                rows.add(new EncounterRow(encounter, patient, provider));
            }
            int drafts = context.encounters().drafts();
            return new Snapshot(rows, drafts);
        }, snapshot -> {
            allRows.clear();
            allRows.addAll(snapshot.rows());
            draftLabel.setText(snapshot.drafts() + " draft" + (snapshot.drafts() == 1 ? "" : "s"));
            applyFilter();
        }, throwable -> {
            countLabel.setText("Unable to load encounters");
            Dialogs.error(throwable);
        });
    }

    private void applyFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String status = statusFilter.getSelectionModel().getSelectedItem();
        List<EncounterRow> filtered = allRows.stream()
                .filter(row -> query.isBlank() || row.matches(query))
                .filter(row -> status == null || status.startsWith("All")
                        || row.encounter().status().name().equals(status))
                .toList();
        encounterTable.getItems().setAll(filtered);
        countLabel.setText(filtered.size() + " encounter" + (filtered.size() == 1 ? "" : "s"));
        openButton.setDisable(filtered.isEmpty());
    }

    @FXML
    private void onRefresh() {
        load();
    }

    @FXML
    private void onOpen() {
        EncounterRow selected = encounterTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Dialogs.info("Select an encounter first.");
            return;
        }
        context.navigator().show("encounter-editor", selected.encounter().id());
    }

    @FXML
    private void onNew() {
        PatientPicker.chooseId(context, "New encounter").ifPresent(patientId ->
                Async.run(() -> context.encounters().start(patientId, null, null, null), encounter -> {
                    Toasts.success("Encounter started");
                    context.navigator().show("encounter-editor", encounter.id());
                }));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private record Snapshot(List<EncounterRow> rows, int drafts) {
    }

    private record EncounterRow(Encounter encounter, Patient patient, User provider) {
        String patientName() {
            return patient == null ? "(unknown)" : patient.fullName().trim();
        }

        String mrn() {
            return patient == null ? "—" : patient.mrn();
        }

        String providerName() {
            return provider == null ? "—" : provider.fullName();
        }

        boolean matches(String query) {
            return patientName().toLowerCase(Locale.ROOT).contains(query)
                    || mrn().toLowerCase(Locale.ROOT).contains(query)
                    || providerName().toLowerCase(Locale.ROOT).contains(query);
        }
    }
}
