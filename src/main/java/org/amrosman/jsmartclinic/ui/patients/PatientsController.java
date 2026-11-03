package org.amrosman.jsmartclinic.ui.patients;

import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.util.Duration;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.app.ContextAware;
import org.amrosman.jsmartclinic.app.Navigator;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.service.Permission;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Dialogs;
import org.amrosman.jsmartclinic.ui.components.Toasts;
import org.amrosman.jsmartclinic.ui.components.Ui;
import org.amrosman.jsmartclinic.util.Dates;

import java.util.List;

/** Searchable, paginated patient registry. */
public class PatientsController implements ContextAware, Navigator.Refreshable {

    @FXML private TextField searchField;
    @FXML private CheckBox archivedCheck;
    @FXML private TableView<Patient> patientTable;
    @FXML private TableColumn<Patient, String> nameColumn;
    @FXML private TableColumn<Patient, String> mrnColumn;
    @FXML private TableColumn<Patient, String> phoneColumn;
    @FXML private TableColumn<Patient, String> dobColumn;
    @FXML private TableColumn<Patient, String> ageColumn;
    @FXML private TableColumn<Patient, String> statusColumn;
    @FXML private Button chartButton;
    @FXML private Button editButton;
    @FXML private Button archiveButton;
    @FXML private Button newButton;
    @FXML private Button previousButton;
    @FXML private Button nextButton;
    @FXML private Label pageLabel;
    @FXML private Label countLabel;

    private static final int PAGE_SIZE = 20;

    private AppContext context;
    private int page;
    private int total;
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));

    @Override
    public void init(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        if (context == null) {
            context = AppContext.get();
        }
        patientTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().fullName().trim()));
        mrnColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().mrn()));
        phoneColumn.setCellValueFactory(data -> new SimpleStringProperty(text(data.getValue().phone())));
        dobColumn.setCellValueFactory(data -> new SimpleStringProperty(Dates.displayDate(data.getValue().dateOfBirth())));
        ageColumn.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().age())));
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().deletedAt() == null ? "ACTIVE" : "ARCHIVED"));
        statusColumn.setCellFactory(Ui.badgeCell());
        patientTable.setPlaceholder(Ui.emptyState("No patients yet", "Register the first patient to get started"));

        webEvents();
        applyPermissions();
        debounce.setOnFinished(event -> {
            page = 0;
            load();
        });
        searchField.textProperty().addListener((obs, old, value) -> debounce.playFromStart());
        archivedCheck.selectedProperty().addListener((obs, old, value) -> {
            page = 0;
            load();
        });
        load();
    }

    private void webEvents() {
        patientTable.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> updateButtons());
        patientTable.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2
                    && patientTable.getSelectionModel().getSelectedItem() != null) {
                onOpenChart();
            }
        });
        patientTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                onOpenChart();
            }
        });
    }

    private void applyPermissions() {
        boolean canEdit = context.session().can(Permission.PATIENT_EDIT);
        newButton.setVisible(canEdit);
        newButton.setManaged(canEdit);
        editButton.setVisible(canEdit);
        editButton.setManaged(canEdit);
        archiveButton.setVisible(canEdit);
        archiveButton.setManaged(canEdit);
        boolean canChart = context.session().can(Permission.VIEW_CLINICAL);
        chartButton.setVisible(canChart);
        chartButton.setManaged(canChart);
        updateButtons();
    }

    @Override
    public void refresh() {
        load();
    }

    private void load() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        boolean archived = archivedCheck.isSelected();
        int requestedPage = page;
        countLabel.setText("Loading…");
        Async.run(() -> {
            List<Patient> rows = context.patients().search(query, archived, requestedPage, PAGE_SIZE);
            int count = context.patients().count(query, archived);
            return new Page(rows, count);
        }, result -> {
            if (requestedPage > 0 && result.rows().isEmpty() && result.total() > 0) {
                page = Math.max(0, (result.total() - 1) / PAGE_SIZE);
                load();
                return;
            }
            total = result.total();
            patientTable.getItems().setAll(result.rows());
            int pages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
            pageLabel.setText("Page " + (page + 1) + " of " + pages);
            countLabel.setText(total + " patient" + (total == 1 ? "" : "s"));
            previousButton.setDisable(page <= 0);
            nextButton.setDisable(page + 1 >= pages);
            updateButtons();
        }, throwable -> {
            countLabel.setText("Unable to load patients");
            Dialogs.error(throwable);
        });
    }

    private void updateButtons() {
        Patient selected = patientTable.getSelectionModel().getSelectedItem();
        boolean none = selected == null;
        chartButton.setDisable(none || !context.session().can(Permission.VIEW_CLINICAL));
        editButton.setDisable(none || !context.session().can(Permission.PATIENT_EDIT));
        boolean canEdit = context.session().can(Permission.PATIENT_EDIT);
        archiveButton.setDisable(none || !canEdit);
        if (selected != null && selected.deletedAt() != null) {
            archiveButton.setText("Restore");
            archiveButton.getStyleClass().setAll("secondary-button");
        } else {
            archiveButton.setText("Archive");
            archiveButton.getStyleClass().setAll("danger-button");
        }
    }

    @FXML
    private void onNew() {
        PatientDialog.open(context, null, saved -> {
            Toasts.success("Patient " + saved.fullName().trim() + " registered");
            refresh();
            context.navigator().show("patient-chart", saved.id());
        });
    }

    @FXML
    private void onEdit() {
        Patient selected = patientTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        PatientDialog.open(context, selected, saved -> {
            Toasts.success("Patient updated");
            refresh();
        });
    }

    @FXML
    private void onOpenChart() {
        Patient selected = patientTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (!context.session().can(Permission.VIEW_CLINICAL)) {
            Dialogs.info("Your role cannot open clinical charts.");
            return;
        }
        context.navigator().show("patient-chart", selected.id());
    }

    @FXML
    private void onArchive() {
        Patient selected = patientTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        boolean restoring = selected.deletedAt() != null;
        String action = restoring ? "Restore" : "Archive";
        if (!Dialogs.confirm(action + " patient",
                action + " " + selected.fullName().trim() + " (" + selected.mrn() + ")?")) {
            return;
        }
        Async.run(() -> {
            if (restoring) {
                context.patients().restore(selected.id());
            } else {
                context.patients().archive(selected.id());
            }
            return null;
        }, ignored -> {
            Toasts.success("Patient " + (restoring ? "restored" : "archived"));
            load();
        });
    }

    @FXML
    private void onPrevious() {
        if (page > 0) {
            page--;
            load();
        }
    }

    @FXML
    private void onNext() {
        page++;
        load();
    }

    private static String text(String value) {
        return value == null ? "—" : value;
    }

    private record Page(List<Patient> rows, int total) {
    }
}
