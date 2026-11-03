package org.amrosman.jsmartclinic.ui.appointments;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.app.ContextAware;
import org.amrosman.jsmartclinic.app.Navigator;
import org.amrosman.jsmartclinic.domain.AppointmentStatus;
import org.amrosman.jsmartclinic.domain.User;
import org.amrosman.jsmartclinic.service.Permission;
import org.amrosman.jsmartclinic.service.SchedulingService;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Dialogs;
import org.amrosman.jsmartclinic.ui.components.Toasts;
import org.amrosman.jsmartclinic.ui.components.Ui;
import org.amrosman.jsmartclinic.util.Dates;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/** Day/week calendar and waiting room. */
public class AppointmentsController implements ContextAware, Navigator.Refreshable {

    private static final int FIRST_HOUR = 8;
    private static final int LAST_HOUR = 19;

    @FXML private ComboBox<User> providerCombo;
    @FXML private DatePicker datePicker;
    @FXML private ToggleButton dayToggle;
    @FXML private ToggleButton weekToggle;
    @FXML private ToggleGroup viewGroup;
    @FXML private Label selectedLabel;
    @FXML private Label calendarTitle;
    @FXML private Label permissionHint;
    @FXML private GridPane calendarGrid;
    @FXML private Label waitingCount;
    @FXML private TableView<SchedulingService.ScheduleEntry> waitingTable;
    @FXML private TableColumn<SchedulingService.ScheduleEntry, String> waitingPatient;
    @FXML private TableColumn<SchedulingService.ScheduleEntry, String> waitingTime;
    @FXML private TableColumn<SchedulingService.ScheduleEntry, String> waitingMinutes;
    @FXML private TableColumn<SchedulingService.ScheduleEntry, String> waitingStatus;
    @FXML private Button newButton;
    @FXML private Button checkInButton;
    @FXML private Button startButton;
    @FXML private Button completeButton;
    @FXML private Button cancelButton;
    @FXML private Button openChartButton;
    @FXML private Button encounterButton;
    @FXML private Button rescheduleButton;

    private AppContext context;
    private SchedulingService.ScheduleEntry selected;

    @Override
    public void init(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        if (context == null) {
            context = AppContext.get();
        }
        datePicker.setValue(LocalDate.now());
        setupWaitingTable();
        providerCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(User value) {
                return value == null ? "" : value.fullName();
            }

            @Override
            public User fromString(String string) {
                return null;
            }
        });
        providerCombo.valueProperty().addListener((obs, old, value) -> load());
        datePicker.valueProperty().addListener((obs, old, value) -> load());
        viewGroup.selectedToggleProperty().addListener((obs, old, value) -> {
            if (value == null) {
                dayToggle.setSelected(true);
            }
            load();
        });
        waitingTable.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            if (value != null) {
                selected = value;
                renderSelection();
            }
        });
        applyPermissions();
        loadProviders();
    }

    private void setupWaitingTable() {
        waitingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        waitingPatient.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().patientName()));
        waitingTime.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().timeLabel()));
        waitingMinutes.setCellValueFactory(data -> new SimpleStringProperty(
                context.scheduling().minutesWaiting(data.getValue()) + " min"));
        waitingStatus.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().appointment().status().name()));
        waitingStatus.setCellFactory(Ui.badgeCell());
        waitingTable.setPlaceholder(Ui.emptyState("Waiting room is empty", "No checked-in patients"));
        waitingTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                onOpenChart();
            }
        });
    }

    private void applyPermissions() {
        boolean canSchedule = context.session().can(Permission.SCHEDULE);
        newButton.setVisible(canSchedule);
        newButton.setManaged(canSchedule);
        checkInButton.setVisible(canSchedule);
        checkInButton.setManaged(canSchedule);
        startButton.setVisible(canSchedule);
        startButton.setManaged(canSchedule);
        completeButton.setVisible(canSchedule);
        completeButton.setManaged(canSchedule);
        cancelButton.setVisible(canSchedule);
        cancelButton.setManaged(canSchedule);
        rescheduleButton.setVisible(canSchedule);
        rescheduleButton.setManaged(canSchedule);
        boolean canChart = context.session().can(Permission.VIEW_CLINICAL);
        openChartButton.setVisible(canChart);
        openChartButton.setManaged(canChart);
        boolean canEncounter = context.session().can(Permission.ENCOUNTERS);
        encounterButton.setVisible(canEncounter);
        encounterButton.setManaged(canEncounter);
        boolean restricted = !canSchedule || !canChart || !canEncounter;
        permissionHint.setText(restricted
                ? "Some scheduling and clinical actions are hidden because your role does not allow them."
                : "");
        permissionHint.setVisible(restricted);
        permissionHint.setManaged(restricted);
        updateButtons();
    }

    private void loadProviders() {
        Async.run(() -> context.users().providers(), providers -> {
            providerCombo.getItems().setAll(providers);
            User current = context.session().current();
            User selection = providers.stream().filter(p -> current != null && p.id() == current.id())
                    .findFirst().orElse(providers.isEmpty() ? null : providers.get(0));
            providerCombo.getSelectionModel().select(selection);
            if (selection == null) {
                load();
            }
        }, throwable -> {
            Dialogs.error(throwable);
            load();
        });
    }

    @Override
    public void refresh() {
        load();
    }

    private void load() {
        LocalDate date = datePicker.getValue() == null ? LocalDate.now() : datePicker.getValue();
        User provider = providerCombo.getSelectionModel().getSelectedItem();
        Long providerId = provider == null ? null : provider.id();
        boolean week = weekToggle.isSelected();
        Async.run(() -> {
            List<SchedulingService.ScheduleEntry> entries = week
                    ? context.scheduling().week(providerId, date)
                    : context.scheduling().day(providerId, date);
            List<SchedulingService.ScheduleEntry> waiting = context.session().can(Permission.SCHEDULE)
                    ? context.scheduling().waitingRoom() : List.of();
            return new CalendarData(entries, waiting);
        }, data -> {
            if (selected != null && data.entries().stream()
                    .noneMatch(e -> e.appointment().id() == selected.appointment().id())) {
                selected = null;
            }
            render(data.entries(), date, week);
            waitingTable.getItems().setAll(data.waiting());
            waitingCount.setText(data.waiting().size() + " waiting");
            renderSelection();
        }, Dialogs::error);
    }

    private void render(List<SchedulingService.ScheduleEntry> entries, LocalDate date, boolean week) {
        calendarGrid.getChildren().clear();
        calendarGrid.getColumnConstraints().clear();
        calendarGrid.getRowConstraints().clear();
        calendarTitle.setText(week ? "Week of " + Dates.displayDate(monday(date))
                : "Schedule for " + Dates.displayDate(date));

        ColumnConstraints timeColumn = new ColumnConstraints(70);
        calendarGrid.getColumnConstraints().add(timeColumn);
        int days = week ? 7 : 1;
        for (int i = 0; i < days; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setHgrow(Priority.ALWAYS);
            column.setFillWidth(true);
            calendarGrid.getColumnConstraints().add(column);
        }

        Label corner = new Label("Time");
        corner.getStyleClass().add("calendar-head");
        calendarGrid.add(corner, 0, 0);
        LocalDate monday = monday(date);
        for (int day = 0; day < days; day++) {
            LocalDate columnDate = week ? monday.plusDays(day) : date;
            Label header = new Label(week
                    ? columnDate.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.US) + " "
                    + columnDate.format(DateTimeFormatter.ofPattern("d MMM"))
                    : columnDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.US)
                    + " · " + columnDate.format(DateTimeFormatter.ofPattern("d MMM")));
            header.getStyleClass().add("calendar-head");
            header.setMaxWidth(Double.MAX_VALUE);
            header.setAlignment(Pos.CENTER);
            calendarGrid.add(header, day + 1, 0);
        }

        RowConstraints headerRow = new RowConstraints(32);
        calendarGrid.getRowConstraints().add(headerRow);
        for (int hour = FIRST_HOUR; hour <= LAST_HOUR; hour++) {
            RowConstraints row = new RowConstraints(64);
            calendarGrid.getRowConstraints().add(row);
            Label time = new Label(String.format(Locale.US, "%02d:00", hour));
            time.getStyleClass().add("calendar-date");
            time.setAlignment(Pos.TOP_LEFT);
            calendarGrid.add(time, 0, hour - FIRST_HOUR + 1);
            for (int day = 0; day < days; day++) {
                LocalDate columnDate = week ? monday.plusDays(day) : date;
                VBox cell = new VBox(3);
                cell.getStyleClass().add("calendar-cell");
                if (columnDate.equals(LocalDate.now())) {
                    cell.getStyleClass().add("calendar-cell-today");
                }
                cell.setMaxWidth(Double.MAX_VALUE);
                calendarGrid.add(cell, day + 1, hour - FIRST_HOUR + 1);
            }
        }

        for (SchedulingService.ScheduleEntry entry : entries) {
            var local = entry.appointment().startAt().atZone(java.time.ZoneId.systemDefault());
            int row = Math.max(0, Math.min(LAST_HOUR - FIRST_HOUR, local.getHour() - FIRST_HOUR)) + 1;
            int column = week
                    ? local.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue() + 1
                    : 1;
            if (column < 1 || column > days) {
                continue;
            }
            VBox cell = (VBox) calendarGrid.getChildren().stream()
                    .filter(node -> GridPane.getRowIndex(node) != null && GridPane.getColumnIndex(node) != null)
                    .filter(node -> GridPane.getRowIndex(node) == row && GridPane.getColumnIndex(node) == column)
                    .filter(node -> node instanceof VBox)
                    .findFirst().orElse(null);
            if (cell == null) {
                continue;
            }
            Button item = new Button(entry.timeLabel() + "  " + entry.patientName());
            item.getStyleClass().addAll("calendar-entry",
                    "calendar-entry-" + Ui.css(entry.appointment().status().name()));
            item.setMaxWidth(Double.MAX_VALUE);
            item.setTooltip(new javafx.scene.control.Tooltip(
                    entry.patientName() + "\n" + entry.appointment().status().label()
                            + (entry.appointment().reason() == null ? "" : "\n" + entry.appointment().reason())));
            item.setOnAction(event -> {
                selected = entry;
                renderSelection();
            });
            item.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    onReschedule();
                }
            });
            cell.getChildren().add(item);
        }
    }

    private void renderSelection() {
        if (selected == null) {
            selectedLabel.setText("");
            updateButtons();
            return;
        }
        selectedLabel.setText(selected.patientName() + " · " + selected.timeLabel()
                + " · " + selected.appointment().status().label());
        updateButtons();
    }

    private void updateButtons() {
        boolean canSchedule = context.session().can(Permission.SCHEDULE);
        boolean has = selected != null;
        AppointmentStatus status = has ? selected.appointment().status() : null;
        checkInButton.setDisable(!canSchedule || !has || status != AppointmentStatus.SCHEDULED);
        startButton.setDisable(!canSchedule || !has || status != AppointmentStatus.CHECKED_IN);
        completeButton.setDisable(!canSchedule || !has || status != AppointmentStatus.IN_PROGRESS
                && (status == null || status != AppointmentStatus.CHECKED_IN));
        cancelButton.setDisable(!canSchedule || !has || status == AppointmentStatus.COMPLETED
                || status == AppointmentStatus.CANCELLED || status == AppointmentStatus.NO_SHOW);
        openChartButton.setDisable(!has);
        encounterButton.setDisable(!has || status == AppointmentStatus.CANCELLED
                || status == AppointmentStatus.NO_SHOW);
    }

    @FXML
    private void onToday() {
        datePicker.setValue(LocalDate.now());
    }

    @FXML
    private void onNew() {
        AppointmentDialog.open(context, null, null, id -> {
            Toasts.success("Appointment booked");
            load();
        });
    }

    @FXML
    private void onReschedule() {
        if (selected == null) {
            Dialogs.info("Select an appointment first.");
            return;
        }
        AppointmentDialog.open(context, selected.appointment(), null, id -> {
            Toasts.success("Appointment updated");
            load();
        });
    }

    @FXML
    private void onCheckIn() {
        changeStatus(AppointmentStatus.CHECKED_IN);
    }

    @FXML
    private void onStartVisit() {
        changeStatus(AppointmentStatus.IN_PROGRESS);
    }

    @FXML
    private void onComplete() {
        changeStatus(AppointmentStatus.COMPLETED);
    }

    @FXML
    private void onCancel() {
        if (selected == null) {
            return;
        }
        if (!Dialogs.confirm("Cancel appointment", "Cancel the appointment for "
                + selected.patientName() + "?")) {
            return;
        }
        changeStatus(AppointmentStatus.CANCELLED);
    }

    private void changeStatus(AppointmentStatus target) {
        if (selected == null) {
            return;
        }
        long id = selected.appointment().id();
        Async.run(() -> {
            context.scheduling().changeStatus(id, target);
            return null;
        }, ignored -> {
            Toasts.success("Status changed to " + target.label());
            load();
        });
    }

    @FXML
    private void onOpenChart() {
        if (selected == null) {
            return;
        }
        if (!context.session().can(Permission.VIEW_CLINICAL)) {
            Dialogs.info("Your role cannot open clinical charts.");
            return;
        }
        context.navigator().show("patient-chart", selected.appointment().patientId());
    }

    @FXML
    private void onStartEncounter() {
        if (selected == null) {
            return;
        }
        long patientId = selected.appointment().patientId();
        long appointmentId = selected.appointment().id();
        Async.run(() -> context.encounters().start(patientId, appointmentId, null, null), encounter -> {
            Toasts.success("Encounter started");
            context.navigator().show("encounter-editor", encounter.id());
        });
    }

    private static LocalDate monday(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }

    private record CalendarData(List<SchedulingService.ScheduleEntry> entries,
                                List<SchedulingService.ScheduleEntry> waiting) {
    }
}
