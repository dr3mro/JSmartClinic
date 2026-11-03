package org.amrosman.jsmartclinic.ui.labs;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.fxml.FXML;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.app.ContextAware;
import org.amrosman.jsmartclinic.app.Navigator;
import org.amrosman.jsmartclinic.domain.LabOrderStatus;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.repository.LabRepository;
import org.amrosman.jsmartclinic.service.Permission;
import org.amrosman.jsmartclinic.ui.ThemeManager;
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

/** Laboratory worklist with result entry and trend charts. */
public class LabsController implements ContextAware, Navigator.Refreshable {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Label countLabel;
    @FXML private Label pendingLabel;
    @FXML private TableView<LabLine> labTable;
    @FXML private TableColumn<LabLine, String> orderedColumn;
    @FXML private TableColumn<LabLine, String> patientColumn;
    @FXML private TableColumn<LabLine, String> testColumn;
    @FXML private TableColumn<LabLine, String> valueColumn;
    @FXML private TableColumn<LabLine, String> unitColumn;
    @FXML private TableColumn<LabLine, String> referenceColumn;
    @FXML private TableColumn<LabLine, String> flagColumn;
    @FXML private TableColumn<LabLine, String> statusColumn;
    @FXML private Button trendButton;
    @FXML private Button collectedButton;
    @FXML private Button resultButton;
    @FXML private Button cancelButton;
    @FXML private Button orderButton;

    private AppContext context;
    private final List<LabLine> allLines = new ArrayList<>();

    @Override
    public void init(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        if (context == null) {
            context = AppContext.get();
        }
        statusFilter.getItems().setAll("All statuses", "ORDERED", "COLLECTED", "RESULTED", "CANCELLED");
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.valueProperty().addListener((obs, old, value) -> applyFilter());
        searchField.textProperty().addListener((obs, old, value) -> applyFilter());

        labTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        orderedColumn.setCellValueFactory(data -> new SimpleStringProperty(Dates.display(data.getValue().row().orderedAt())));
        patientColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().patientName()));
        testColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().row().testName()));
        valueColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().valueText()));
        unitColumn.setCellValueFactory(data -> new SimpleStringProperty(
                blank(data.getValue().row().unit())));
        referenceColumn.setCellValueFactory(data -> new SimpleStringProperty(LabResultDialog.reference(data.getValue().row())));
        flagColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().row().flag() == null
                ? "—" : data.getValue().row().flag().name()));
        flagColumn.setCellFactory(Ui.badgeCell());
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().row().orderStatus().name()));
        statusColumn.setCellFactory(Ui.badgeCell());
        labTable.setPlaceholder(Ui.emptyState("No lab orders", "Order labs from a patient chart or here"));
        labTable.getSelectionModel().selectedItemProperty().addListener((obs, old, value) ->
                updateButtons());
        labTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                onResult();
            }
        });

        boolean allowed = context.session().can(Permission.LABS);
        orderButton.setVisible(allowed);
        orderButton.setManaged(allowed);
        collectedButton.setVisible(allowed);
        collectedButton.setManaged(allowed);
        resultButton.setVisible(allowed);
        resultButton.setManaged(allowed);
        cancelButton.setVisible(allowed);
        cancelButton.setManaged(allowed);
        trendButton.setVisible(allowed);
        trendButton.setManaged(allowed);
        load();
    }

    @Override
    public void refresh() {
        load();
    }

    private void load() {
        countLabel.setText("Loading…");
        Async.run(() -> {
            List<LabRepository.LabRow> rows = context.labs().recent(300);
            Map<Long, Patient> cache = new HashMap<>();
            List<LabLine> lines = new ArrayList<>(rows.size());
            for (LabRepository.LabRow row : rows) {
                Patient patient = cache.computeIfAbsent(row.patientId(), id -> context.patients().basic(id));
                lines.add(new LabLine(row, patient));
            }
            int pending = context.labs().pendingCount();
            return new Snapshot(lines, pending);
        }, snapshot -> {
            allLines.clear();
            allLines.addAll(snapshot.lines());
            pendingLabel.setText(snapshot.pending() + " pending");
            applyFilter();
        }, throwable -> {
            countLabel.setText("Unable to load labs");
            Dialogs.error(throwable);
        });
    }

    private void applyFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String status = statusFilter.getSelectionModel().getSelectedItem();
        List<LabLine> filtered = allLines.stream()
                .filter(line -> query.isBlank() || line.matches(query))
                .filter(line -> status == null || status.startsWith("All")
                        || line.row().orderStatus().name().equals(status))
                .toList();
        labTable.getItems().setAll(filtered);
        countLabel.setText(filtered.size() + " line" + (filtered.size() == 1 ? "" : "s"));
        updateButtons();
    }

    private void updateButtons() {
        LabLine selected = labTable.getSelectionModel().getSelectedItem();
        boolean none = selected == null;
        boolean pending = !none && selected.row().orderStatus() != LabOrderStatus.RESULTED
                && selected.row().orderStatus() != LabOrderStatus.CANCELLED;
        collectedButton.setDisable(none || selected.row().orderStatus() != LabOrderStatus.ORDERED);
        resultButton.setDisable(none || !pending);
        cancelButton.setDisable(none || selected.row().orderStatus() == LabOrderStatus.RESULTED
                || selected.row().orderStatus() == LabOrderStatus.CANCELLED);
        trendButton.setDisable(none);
    }

    @FXML
    private void onRefresh() {
        load();
    }

    @FXML
    private void onOrder() {
        PatientPicker.chooseId(context, "Order labs").ifPresent(patientId ->
                LabOrderDialog.open(context, patientId, null, id -> {
                    Toasts.success("Lab order placed");
                    load();
                }));
    }

    @FXML
    private void onResult() {
        LabLine selected = labTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Dialogs.info("Select a lab line first.");
            return;
        }
        LabResultDialog.open(context, selected.row(), flag -> load());
    }

    @FXML
    private void onCollected() {
        LabLine selected = labTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Async.run(() -> {
            context.labs().markCollected(selected.row().orderId(), selected.row().patientId());
            return null;
        }, ignored -> {
            Toasts.success("Marked as collected");
            load();
        });
    }

    @FXML
    private void onCancelOrder() {
        LabLine selected = labTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (!Dialogs.confirm("Cancel lab order", "Cancel this order for " + selected.patientName() + "?")) {
            return;
        }
        Async.run(() -> {
            context.labs().cancel(selected.row().orderId(), selected.row().patientId());
            return null;
        }, ignored -> {
            Toasts.success("Lab order cancelled");
            load();
        });
    }

    @FXML
    private void onTrend() {
        LabLine selected = labTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Dialogs.info("Select a result first.");
            return;
        }
        Async.run(() -> context.labs().trend(selected.row().patientId(), selected.row().testId()),
                rows -> showTrend(selected.row().testName(), selected.row().unit(), rows));
    }

    private void showTrend(String name, String unit, List<LabRepository.LabRow> rows) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(unit == null ? name : name + " (" + unit + ")");
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(name + " trend");
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(name);
        for (LabRepository.LabRow row : rows) {
            if (row.valueNum() == null) {
                continue;
            }
            var date = row.resultedAt() == null ? row.orderedAt() : row.resultedAt();
            series.getData().add(new XYChart.Data<>(Dates.displayDate(date), row.valueNum()));
        }
        chart.getData().add(series);
        chart.setPrefSize(680, 420);
        chart.setLegendVisible(false);
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Lab trend");
        dialog.setHeaderText(name + " · " + rows.size() + " result(s)");
        dialog.getDialogPane().setContent(chart);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ThemeManager.applyToDialog(dialog.getDialogPane());
        dialog.showAndWait();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private record Snapshot(List<LabLine> lines, int pending) {
    }

    private record LabLine(LabRepository.LabRow row, Patient patient) {
        String patientName() {
            return patient == null ? "(unknown)" : patient.fullName().trim();
        }

        String valueText() {
            if (row.valueNum() != null) {
                return row.valueNum() == Math.rint(row.valueNum())
                        ? String.valueOf(row.valueNum().longValue()) : String.valueOf(row.valueNum());
            }
            return row.valueText() == null || row.valueText().isBlank() ? "Pending" : row.valueText();
        }

        boolean matches(String query) {
            return patientName().toLowerCase(Locale.ROOT).contains(query)
                    || row.testName().toLowerCase(Locale.ROOT).contains(query);
        }
    }
}
