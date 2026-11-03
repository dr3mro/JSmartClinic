package org.amrosman.jsmartclinic;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public class HelloController {

    /** Simple in-memory patient model (replace with your database layer later). */
    public record Patient(String id, String name, int age, String gender, String lastVisit, String status,
                          String bp, String hr, String temp, String spo2,
                          String allergies, String conditions) { }

    @FXML private ToggleGroup navGroup;
    @FXML private Label pageTitle;
    @FXML private Label dateLabel;
    @FXML private TextField searchField;

    @FXML private Label statAppointments;
    @FXML private Label statWaiting;
    @FXML private Label statNew;
    @FXML private Label statLabs;

    @FXML private Label patientCount;
    @FXML private TableView<Patient> patientTable;
    @FXML private TableColumn<Patient, String> colId;
    @FXML private TableColumn<Patient, String> colName;
    @FXML private TableColumn<Patient, String> colAge;
    @FXML private TableColumn<Patient, String> colGender;
    @FXML private TableColumn<Patient, String> colLastVisit;
    @FXML private TableColumn<Patient, String> colStatus;

    @FXML private Label avatarLabel;
    @FXML private Label summaryName;
    @FXML private Label summaryMeta;
    @FXML private Label bpValue;
    @FXML private Label hrValue;
    @FXML private Label tempValue;
    @FXML private Label spo2Value;
    @FXML private Label allergiesValue;
    @FXML private Label conditionsValue;

    @FXML private ListView<String> scheduleList;

    private final ObservableList<Patient> patients = FXCollections.observableArrayList(
            new Patient("P-1001", "Mona Hassan", 54, "Female", "2026-11-02", "Waiting",
                    "138/86", "78 bpm", "36.8 °C", "97 %", "Penicillin", "Hypertension, Type 2 diabetes"),
            new Patient("P-1002", "James Carter", 61, "Male", "2026-11-01", "Follow-up",
                    "142/90", "72 bpm", "36.6 °C", "96 %", "None known", "Coronary artery disease"),
            new Patient("P-1003", "Sara Ibrahim", 33, "Female", "2026-10-30", "Active",
                    "118/76", "68 bpm", "36.7 °C", "99 %", "Sulfa drugs", "Asthma"),
            new Patient("P-1004", "Daniel Lee", 47, "Male", "2026-10-28", "Active",
                    "126/82", "80 bpm", "37.1 °C", "98 %", "Latex", "Hyperlipidemia"),
            new Patient("P-1005", "Fatma Youssef", 70, "Female", "2026-10-25", "Discharged",
                    "130/80", "64 bpm", "36.5 °C", "95 %", "None known", "Atrial fibrillation"),
            new Patient("P-1006", "Michael Brown", 39, "Male", "2026-10-22", "Waiting",
                    "120/78", "88 bpm", "38.2 °C", "97 %", "Ibuprofen", "Acute bronchitis"),
            new Patient("P-1007", "Nour El-Sayed", 28, "Female", "2026-10-20", "Follow-up",
                    "112/70", "74 bpm", "36.9 °C", "99 %", "None known", "Migraine"),
            new Patient("P-1008", "Robert Wilson", 66, "Male", "2026-10-18", "Active",
                    "150/94", "70 bpm", "36.6 °C", "94 %", "Aspirin", "Heart failure, CKD stage 3")
    );

    @FXML
    private void initialize() {
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)));

        // Sidebar navigation: keep one item selected and update the title
        navGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == null) {
                oldT.setSelected(true);
            } else {
                pageTitle.setText(String.valueOf(newT.getUserData()));
            }
        });

        setupTable();
        setupSchedule();
        updateStats();
    }

    private void setupTable() {
        colId.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().id()));
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        colAge.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().age())));
        colGender.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().gender()));
        colLastVisit.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().lastVisit()));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status()));

        // Colored status badge
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().addAll("badge", "badge-" + item.toLowerCase(Locale.ROOT).replace(' ', '-'));
                    setGraphic(badge);
                }
            }
        });

        FilteredList<Patient> filtered = new FilteredList<>(patients, p -> true);
        searchField.textProperty().addListener((obs, o, text) -> {
            String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(p -> q.isEmpty()
                    || p.name().toLowerCase(Locale.ROOT).contains(q)
                    || p.id().toLowerCase(Locale.ROOT).contains(q));
            patientCount.setText(filtered.size() + " patients");
        });

        SortedList<Patient> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(patientTable.comparatorProperty());
        patientTable.setItems(sorted);
        patientTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        patientTable.setPlaceholder(new Label("No patients match your search"));

        patientTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, p) -> showPatient(p));
        patientTable.getSelectionModel().selectFirst();
        patientCount.setText(patients.size() + " patients");
    }

    private void showPatient(Patient p) {
        if (p == null) {
            return;
        }
        String initials = Arrays.stream(p.name().split("\\s+"))
                .filter(s -> !s.isEmpty())
                .limit(2)
                .map(s -> s.substring(0, 1).toUpperCase(Locale.ROOT))
                .collect(Collectors.joining());
        avatarLabel.setText(initials);
        summaryName.setText(p.name());
        summaryMeta.setText(p.id() + "  •  " + p.age() + " yrs  •  " + p.gender());
        bpValue.setText(p.bp());
        hrValue.setText(p.hr());
        tempValue.setText(p.temp());
        spo2Value.setText(p.spo2());
        allergiesValue.setText(p.allergies());
        conditionsValue.setText(p.conditions());
    }

    private void setupSchedule() {
        scheduleList.setItems(FXCollections.observableArrayList(
                "09:00   •   Mona Hassan   —   Diabetes follow-up",
                "09:30   •   James Carter   —   ECG review",
                "10:15   •   Michael Brown   —   Fever, cough",
                "11:00   •   Nour El-Sayed   —   Migraine follow-up",
                "13:30   •   Robert Wilson   —   Medication adjustment",
                "15:00   •   New patient   —   Initial consultation"
        ));
    }

    private void updateStats() {
        statAppointments.setText(String.valueOf(scheduleList.getItems().size()));
        statWaiting.setText(String.valueOf(patients.stream().filter(p -> p.status().equals("Waiting")).count()));
        statNew.setText("3");
        statLabs.setText("5");
    }

    @FXML
    private void onNewPatient() {
        info("New Patient", "The new patient registration form will open here.");
    }

    @FXML
    private void onOpenChart() {
        Patient p = patientTable.getSelectionModel().getSelectedItem();
        info("Patient Chart", p == null ? "Select a patient first." : "Opening full chart for " + p.name() + ".");
    }

    private void info(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
