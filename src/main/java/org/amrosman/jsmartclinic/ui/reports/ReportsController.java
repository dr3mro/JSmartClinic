package org.amrosman.jsmartclinic.ui.reports;

import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.app.ContextAware;
import org.amrosman.jsmartclinic.app.Navigator;
import org.amrosman.jsmartclinic.repository.ReportRepository.NamedCount;
import org.amrosman.jsmartclinic.repository.ReportRepository.PeriodAmount;
import org.amrosman.jsmartclinic.service.Permission;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Dialogs;
import org.amrosman.jsmartclinic.util.Dates;
import org.amrosman.jsmartclinic.util.Money;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Practice analytics with a date range, charts and CSV/PDF export. */
public class ReportsController implements ContextAware, Navigator.Refreshable {

    private static final DateTimeFormatter RANGE = DateTimeFormatter.ofPattern("MMM d, yyyy");

    @FXML private DatePicker fromDate;
    @FXML private DatePicker toDate;
    @FXML private ComboBox<String> preset;
    @FXML private Label rangeLabel;
    @FXML private Label revenueLabel;
    @FXML private Label appointmentsLabel;
    @FXML private Label topDiagnosisLabel;
    @FXML private Label patientsLabel;
    @FXML private BarChart<String, Number> revenueChart;
    @FXML private BarChart<String, Number> providerChart;
    @FXML private BarChart<String, Number> diagnosisChart;
    @FXML private PieChart demographicsChart;
    @FXML private Button csvButton;
    @FXML private Button pdfButton;

    private AppContext context;
    private Snapshot snapshot;
    private boolean adjusting;

    @Override
    public void init(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        if (context == null) {
            context = AppContext.get();
        }
        boolean allowed = context.session().can(Permission.REPORTS);
        csvButton.setDisable(!allowed);
        pdfButton.setDisable(!allowed);

        preset.getItems().setAll("This month", "Last 30 days", "Last 90 days", "This year", "Custom");
        adjusting = true;
        preset.setValue("This month");
        LocalDate today = LocalDate.now();
        fromDate.setValue(today.withDayOfMonth(1));
        toDate.setValue(today);
        adjusting = false;

        preset.valueProperty().addListener((obs, old, value) -> {
            if (adjusting || value == null) {
                return;
            }
            applyPreset(value);
        });
        fromDate.valueProperty().addListener((obs, old, value) -> dateChanged());
        toDate.valueProperty().addListener((obs, old, value) -> dateChanged());

        rangeLabel.setText("Loading…");
        load();
    }

    @Override
    public void refresh() {
        load();
    }

    private void applyPreset(String value) {
        LocalDate today = LocalDate.now();
        LocalDate from = switch (value) {
            case "Last 30 days" -> today.minusDays(29);
            case "Last 90 days" -> today.minusDays(89);
            case "This year" -> today.withDayOfYear(1);
            default -> today.withDayOfMonth(1);
        };
        adjusting = true;
        fromDate.setValue(from);
        toDate.setValue(today);
        adjusting = false;
        if (!"Custom".equals(value)) {
            load();
        }
    }

    private void dateChanged() {
        if (adjusting) {
            return;
        }
        adjusting = true;
        preset.setValue("Custom");
        adjusting = false;
        load();
    }

    private void load() {
        LocalDate from = fromDate.getValue();
        LocalDate to = toDate.getValue();
        if (from == null || to == null) {
            return;
        }
        if (from.isAfter(to)) {
            rangeLabel.setText("Check the date range");
            Dialogs.warn("The start date must be before the end date.");
            return;
        }
        rangeLabel.setText("Loading…");
        Async.run(() -> new Snapshot(
                context.reports().revenue(from, to),
                context.reports().appointmentsByProvider(from, to),
                context.reports().topDiagnoses(from, to, 8),
                context.reports().demographics(),
                context.reports().ageGroups(),
                context.reports().appointmentsByStatus(from, to),
                context.reports().revenueByMonth(from, to)),
                data -> apply(from, to, data), throwable -> {
                    rangeLabel.setText("Unable to load reports");
                    Dialogs.error(throwable);
                });
    }

    private void apply(LocalDate from, LocalDate to, Snapshot data) {
        snapshot = data;
        long appointments = data.statuses().stream().mapToLong(NamedCount::count).sum();
        long patients = data.demographics().stream().mapToLong(NamedCount::count).sum();

        rangeLabel.setText(RANGE.format(from) + " – " + RANGE.format(to));
        revenueLabel.setText(Money.format(data.revenue()));
        appointmentsLabel.setText(String.valueOf(appointments));
        topDiagnosisLabel.setText(data.diagnoses().isEmpty() ? "—"
                : shorten(data.diagnoses().get(0).label(), 22));
        patientsLabel.setText(String.valueOf(patients));

        setBars(revenueChart, data.months().stream()
                .map(amount -> new Bar(amount.period(), amount.cents() / 100.0))
                .toList());
        setBars(providerChart, data.providers().stream()
                .map(count -> new Bar(count.label(), count.count()))
                .toList());
        setBars(diagnosisChart, data.diagnoses().stream()
                .map(count -> new Bar(shorten(count.label(), 18), count.count()))
                .toList());

        demographicsChart.getData().setAll(data.demographics().stream()
                .map(count -> new PieChart.Data(count.label() == null ? "Unknown" : count.label(), count.count()))
                .toList());
    }

    private static void setBars(BarChart<String, Number> chart, List<Bar> bars) {
        CategoryAxis axis = (CategoryAxis) chart.getXAxis();
        axis.getCategories().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Bar bar : bars) {
            axis.getCategories().add(bar.label());
            series.getData().add(new XYChart.Data<>(bar.label(), bar.value()));
        }
        chart.getData().setAll(series);
    }

    @FXML
    private void onRefresh() {
        load();
    }

    @FXML
    private void onExportCsv() {
        Snapshot data = snapshot;
        if (data == null) {
            return;
        }
        List<String> headers = List.of("Section", "Label", "Value");
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Summary", "Payments collected", Money.format(data.revenue())));
        rows.add(List.of("Summary", "Appointments", data.statuses().stream().mapToLong(NamedCount::count).sum()));
        rows.add(List.of("Summary", "Active patients",
                data.demographics().stream().mapToLong(NamedCount::count).sum()));
        data.months().forEach(amount ->
                rows.add(List.of("Revenue by month", amount.period(), Money.format(amount.cents()))));
        data.providers().forEach(count -> rows.add(List.of("Appointments by provider", count.label(), count.count())));
        data.diagnoses().forEach(count -> rows.add(List.of("Top diagnoses", count.label(), count.count())));
        data.demographics().forEach(count -> rows.add(List.of("Demographics", count.label(), count.count())));
        data.ageGroups().forEach(count -> rows.add(List.of("Age groups", count.label(), count.count())));
        data.statuses().forEach(count -> rows.add(List.of("Appointment status", count.label(), count.count())));

        Async.run(() -> context.reports().exportCsv("practice-report", headers, rows),
                Dialogs::openFile, Dialogs::error);
    }

    @FXML
    private void onExportPdf() {
        Snapshot data = snapshot;
        if (data == null) {
            return;
        }
        List<String> headers = List.of("Section", "Label", "Value");
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Summary", "Payments collected", Money.format(data.revenue())));
        rows.add(List.of("Summary", "Appointments",
                String.valueOf(data.statuses().stream().mapToLong(NamedCount::count).sum())));
        rows.add(List.of("Summary", "Active patients",
                String.valueOf(data.demographics().stream().mapToLong(NamedCount::count).sum())));
        data.months().forEach(amount ->
                rows.add(List.of("Revenue by month", amount.period(), Money.format(amount.cents()))));
        data.providers().forEach(count ->
                rows.add(List.of("Appointments by provider", count.label(), String.valueOf(count.count()))));
        data.diagnoses().forEach(count ->
                rows.add(List.of("Top diagnoses", count.label(), String.valueOf(count.count()))));
        data.demographics().forEach(count ->
                rows.add(List.of("Demographics", count.label(), String.valueOf(count.count()))));
        data.ageGroups().forEach(count ->
                rows.add(List.of("Age groups", count.label(), String.valueOf(count.count()))));
        data.statuses().forEach(count ->
                rows.add(List.of("Appointment status", count.label(), String.valueOf(count.count()))));

        String title = "Practice report " + RANGE.format(fromDate.getValue()) + " – " + RANGE.format(toDate.getValue());
        Async.run(() -> context.reports().exportPdf(title, headers, rows), Dialogs::openFile, Dialogs::error);
    }

    private static String shorten(String value, int max) {
        if (value == null) {
            return "—";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private record Bar(String label, double value) {
    }

    private record Snapshot(long revenue, List<NamedCount> providers, List<NamedCount> diagnoses,
                            List<NamedCount> demographics, List<NamedCount> ageGroups,
                            List<NamedCount> statuses, List<PeriodAmount> months) {
    }
}
