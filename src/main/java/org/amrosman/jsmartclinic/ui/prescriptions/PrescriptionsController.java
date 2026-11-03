package org.amrosman.jsmartclinic.ui.prescriptions;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.app.ContextAware;
import org.amrosman.jsmartclinic.app.Navigator;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.Prescription;
import org.amrosman.jsmartclinic.domain.PrescriptionItem;
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

/** All recent prescriptions with an item preview. */
public class PrescriptionsController implements ContextAware, Navigator.Refreshable {

    @FXML private TextField searchField;
    @FXML private Label countLabel;
    @FXML private Label hintLabel;
    @FXML private TableView<PrescriptionRow> prescriptionTable;
    @FXML private TableColumn<PrescriptionRow, String> issuedColumn;
    @FXML private TableColumn<PrescriptionRow, String> patientColumn;
    @FXML private TableColumn<PrescriptionRow, String> prescriberColumn;
    @FXML private TableColumn<PrescriptionRow, String> itemsColumn;
    @FXML private TableColumn<PrescriptionRow, String> notesColumn;
    @FXML private Label detailTitle;
    @FXML private Label detailMeta;
    @FXML private Label detailNotes;
    @FXML private TableView<PrescriptionItem> itemTable;
    @FXML private TableColumn<PrescriptionItem, String> drugColumn;
    @FXML private TableColumn<PrescriptionItem, String> doseColumn;
    @FXML private TableColumn<PrescriptionItem, String> durationColumn;
    @FXML private TableColumn<PrescriptionItem, String> instructionsColumn;
    @FXML private Button viewButton;
    @FXML private Button printButton;
    @FXML private Button renewButton;
    @FXML private Button newButton;

    private AppContext context;
    private final List<PrescriptionRow> allRows = new ArrayList<>();

    @Override
    public void init(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        if (context == null) {
            context = AppContext.get();
        }
        prescriptionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        issuedColumn.setCellValueFactory(data -> new SimpleStringProperty(Dates.display(data.getValue().prescription().issuedAt())));
        patientColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().patientName()));
        prescriberColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().prescriberName()));
        itemsColumn.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().itemCount())));
        notesColumn.setCellValueFactory(data -> new SimpleStringProperty(
                blank(data.getValue().prescription().notes())));
        prescriptionTable.setPlaceholder(Ui.emptyState("No prescriptions", "Issue the first prescription from a chart"));
        prescriptionTable.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            showDetails(value);
            updateButtons();
        });
        searchField.textProperty().addListener((obs, old, value) -> applyFilter());

        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        drugColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().drugName()
                + (blank(data.getValue().strength()).equals("—") ? "" : " " + data.getValue().strength())));
        doseColumn.setCellValueFactory(data -> new SimpleStringProperty(blank(data.getValue().dose())
                + (blank(data.getValue().frequency()).equals("—") ? "" : " · " + data.getValue().frequency())));
        durationColumn.setCellValueFactory(data -> new SimpleStringProperty(blank(data.getValue().duration())));
        instructionsColumn.setCellValueFactory(data -> new SimpleStringProperty(
                blank(data.getValue().instructions())));
        itemTable.setPlaceholder(Ui.emptyState("No prescription selected", "Choose a prescription to see its items"));

        boolean canPrescribe = context.session().can(Permission.PRESCRIBE);
        newButton.setVisible(canPrescribe);
        newButton.setManaged(canPrescribe);
        renewButton.setVisible(canPrescribe);
        renewButton.setManaged(canPrescribe);
        load();
    }

    @Override
    public void refresh() {
        load();
    }

    private void load() {
        countLabel.setText("Loading…");
        Async.run(() -> {
            List<Prescription> prescriptions = context.prescriptions().recent(250);
            Map<Long, Patient> patientCache = new HashMap<>();
            Map<Long, User> userCache = new HashMap<>();
            List<PrescriptionRow> rows = new ArrayList<>(prescriptions.size());
            for (Prescription prescription : prescriptions) {
                Patient patient = patientCache.computeIfAbsent(prescription.patientId(),
                        id -> context.patients().basic(id));
                User prescriber = userCache.computeIfAbsent(prescription.prescriberId(),
                        id -> context.users().byId(id));
                int count = context.prescriptions().items(prescription.id()).size();
                rows.add(new PrescriptionRow(prescription, patient, prescriber, count));
            }
            return rows;
        }, rows -> {
            allRows.clear();
            allRows.addAll(rows);
            applyFilter();
        }, throwable -> {
            countLabel.setText("Unable to load prescriptions");
            Dialogs.error(throwable);
        });
    }

    private void applyFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        List<PrescriptionRow> filtered = allRows.stream().filter(row -> query.isBlank() || row.matches(query)).toList();
        prescriptionTable.getItems().setAll(filtered);
        countLabel.setText(filtered.size() + " prescription" + (filtered.size() == 1 ? "" : "s"));
        hintLabel.setText(filtered.isEmpty() ? "No matches" : "Double-click to view");
        updateButtons();
    }

    private void showDetails(PrescriptionRow row) {
        itemTable.getItems().clear();
        detailNotes.setText("");
        if (row == null) {
            detailTitle.setText("Prescription details");
            detailMeta.setText("");
            return;
        }
        detailTitle.setText("Prescription #" + row.prescription().id());
        detailMeta.setText(row.patientName() + "   ·   " + Dates.display(row.prescription().issuedAt())
                + "   ·   " + row.prescriberName());
        detailNotes.setText(row.prescription().notes() == null ? "" : row.prescription().notes());
        Async.run(() -> context.prescriptions().items(row.prescription().id()),
                items -> itemTable.getItems().setAll(items));
    }

    private void updateButtons() {
        PrescriptionRow selected = prescriptionTable.getSelectionModel().getSelectedItem();
        boolean none = selected == null;
        viewButton.setDisable(none);
        printButton.setDisable(none);
        renewButton.setDisable(none || !context.session().can(Permission.PRESCRIBE));
    }

    @FXML
    private void onRefresh() {
        load();
    }

    @FXML
    private void onView() {
        PrescriptionRow selected = prescriptionTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            PrescriptionDialog.view(context, selected.prescription());
        }
    }

    @FXML
    private void onPrint() {
        PrescriptionRow selected = prescriptionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Async.run(() -> {
            List<PrescriptionItem> items = context.prescriptions().items(selected.prescription().id());
            var path = context.pdf().prescription(selected.patient(), context.session().current(),
                    selected.prescription(), items);
            context.audit().log(AuditAction.PRINT, "prescription", selected.prescription().id(),
                    selected.prescription().patientId(), "Prescription PDF");
            return path;
        }, Dialogs::openFile, Dialogs::error);
    }

    @FXML
    private void onRenew() {
        PrescriptionRow selected = prescriptionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Async.run(() -> context.prescriptions().renew(selected.prescription().id()), id -> {
            Toasts.success("Prescription renewed");
            load();
        });
    }

    @FXML
    private void onNew() {
        PatientPicker.chooseId(context, "New prescription").ifPresent(patientId ->
                PrescriptionDialog.open(context, patientId, null, id -> {
                    Toasts.success("Prescription issued");
                    load();
                }));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private record PrescriptionRow(Prescription prescription, Patient patient, User prescriber, int itemCount) {
        String patientName() {
            return patient == null ? "(unknown)" : patient.fullName().trim();
        }

        String prescriberName() {
            return prescriber == null ? "—" : prescriber.fullName();
        }

        boolean matches(String query) {
            return patientName().toLowerCase(Locale.ROOT).contains(query)
                    || prescriberName().toLowerCase(Locale.ROOT).contains(query);
        }
    }
}
