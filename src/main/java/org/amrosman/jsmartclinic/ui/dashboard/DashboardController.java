package org.amrosman.jsmartclinic.ui.dashboard;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.app.ContextAware;
import org.amrosman.jsmartclinic.app.Navigator;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.service.Permission;
import org.amrosman.jsmartclinic.service.SchedulingService;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Dialogs;
import org.amrosman.jsmartclinic.ui.components.Ui;
import org.amrosman.jsmartclinic.util.Dates;
import org.amrosman.jsmartclinic.util.Money;

import java.time.LocalDate;
import java.util.List;

/** Live operational dashboard: stat cards, today's schedule and recent patients. */
public class DashboardController implements ContextAware, Navigator.Refreshable {

    @FXML private Label statAppointments;
    @FXML private Label statWaiting;
    @FXML private Label statNew;
    @FXML private Label statLabs;
    @FXML private Label statRevenue;
    @FXML private Label scheduleCount;
    @FXML private TableView<SchedulingService.ScheduleEntry> scheduleTable;
    @FXML private TableColumn<SchedulingService.ScheduleEntry, String> scheduleTime;
    @FXML private TableColumn<SchedulingService.ScheduleEntry, String> schedulePatient;
    @FXML private TableColumn<SchedulingService.ScheduleEntry, String> scheduleProvider;
    @FXML private TableColumn<SchedulingService.ScheduleEntry, String> scheduleReason;
    @FXML private TableColumn<SchedulingService.ScheduleEntry, String> scheduleStatus;
    @FXML private TableView<Patient> recentPatientsTable;
    @FXML private TableColumn<Patient, String> recentName;
    @FXML private TableColumn<Patient, String> recentMrn;
    @FXML private TableColumn<Patient, String> recentAge;
    @FXML private Button newPatientButton;
    @FXML private Button newAppointmentButton;

    private AppContext context;

    @Override
    public void init(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        if (context == null) {
            context = AppContext.get();
        }
        scheduleTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        scheduleTime.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().timeLabel()));
        schedulePatient.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().patientName()));
        scheduleProvider.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().provider() == null ? "—" : data.getValue().provider().fullName()));
        scheduleReason.setCellValueFactory(data -> new SimpleStringProperty(
                blank(data.getValue().appointment().reason())));
        scheduleStatus.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().appointment().status().name()));
        scheduleStatus.setCellFactory(Ui.badgeCell());
        scheduleTable.setPlaceholder(Ui.emptyState("No appointments today", "The schedule is clear"));
        scheduleTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                openChart(scheduleTable.getSelectionModel().getSelectedItem());
            }
        });

        recentPatientsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        recentName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().fullName().trim()));
        recentMrn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().mrn()));
        recentAge.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().age())));
        recentPatientsTable.setPlaceholder(Ui.emptyState("No patients yet", "Register the first patient to get started"));
        recentPatientsTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Patient selected = recentPatientsTable.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    showChart(selected.id());
                }
            }
        });

        boolean canRegister = context.session().can(Permission.PATIENT_EDIT);
        newPatientButton.setVisible(canRegister);
        newPatientButton.setManaged(canRegister);
        boolean canSchedule = context.session().can(Permission.SCHEDULE);
        newAppointmentButton.setVisible(canSchedule);
        newAppointmentButton.setManaged(canSchedule);

        load();
    }

    @Override
    public void refresh() {
        load();
    }

    private void load() {
        LocalDate today = LocalDate.now();
        boolean billing = context.session().can(Permission.BILLING);
        boolean labs = context.session().can(Permission.LABS);
        Async.run(() -> {
            List<SchedulingService.ScheduleEntry> schedule = context.session().can(Permission.SCHEDULE)
                    ? context.scheduling().day(null, today) : List.of();
            List<SchedulingService.ScheduleEntry> waiting = context.session().can(Permission.SCHEDULE)
                    ? context.scheduling().waitingRoom() : List.of();
            List<Patient> recent = context.patients().recent(8);
            int newPatients = context.patients().newThisWeek();
            int pendingLabs = labs ? context.labs().pendingCount() : 0;
            long revenue = billing ? context.billing().todayRevenue() : 0;
            return new Snapshot(schedule, waiting, recent, newPatients, pendingLabs, revenue, billing);
        }, this::apply, Dialogs::error);
    }

    private void apply(Snapshot snapshot) {
        statAppointments.setText(String.valueOf(snapshot.schedule().size()));
        statWaiting.setText(String.valueOf(snapshot.waiting().size()));
        statNew.setText(String.valueOf(snapshot.newPatients()));
        statLabs.setText(String.valueOf(snapshot.pendingLabs()));
        statRevenue.setText(snapshot.billing() ? Money.format(snapshot.revenue()) : "—");
        scheduleTable.getItems().setAll(snapshot.schedule());
        scheduleCount.setText(snapshot.schedule().size() + " appointment"
                + (snapshot.schedule().size() == 1 ? "" : "s"));
        recentPatientsTable.getItems().setAll(snapshot.recent());
    }

    private void openChart(SchedulingService.ScheduleEntry entry) {
        if (entry != null) {
            showChart(entry.appointment().patientId());
        }
    }

    private void showChart(long patientId) {
        if (!context.session().can(Permission.VIEW_CLINICAL)) {
            Dialogs.info("Your role cannot open clinical charts.");
            return;
        }
        context.navigator().show("patient-chart", patientId);
    }

    @FXML
    private void onRefresh() {
        load();
    }

    @FXML
    private void onOpenPatients() {
        context.navigator().show("patients");
    }

    @FXML
    private void onNewPatient() {
        context.navigator().show("patients");
    }

    @FXML
    private void onNewAppointment() {
        context.navigator().show("appointments");
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private record Snapshot(List<SchedulingService.ScheduleEntry> schedule,
                            List<SchedulingService.ScheduleEntry> waiting,
                            List<Patient> recent, int newPatients, int pendingLabs,
                            long revenue, boolean billing) {
    }
}
