package org.amrosman.jsmartclinic.ui.billing;

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
import org.amrosman.jsmartclinic.domain.Invoice;
import org.amrosman.jsmartclinic.domain.InvoiceItem;
import org.amrosman.jsmartclinic.domain.InvoiceStatus;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.Payment;
import org.amrosman.jsmartclinic.service.Permission;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Dialogs;
import org.amrosman.jsmartclinic.ui.components.Toasts;
import org.amrosman.jsmartclinic.ui.components.Ui;
import org.amrosman.jsmartclinic.ui.patients.PatientPicker;
import org.amrosman.jsmartclinic.util.Dates;
import org.amrosman.jsmartclinic.util.Money;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Invoice list with service items, payments, receipts and voiding. */
public class BillingController implements ContextAware, Navigator.Refreshable {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Label countLabel;
    @FXML private Label outstandingLabel;
    @FXML private TableView<InvoiceRow> invoiceTable;
    @FXML private TableColumn<InvoiceRow, String> numberColumn;
    @FXML private TableColumn<InvoiceRow, String> patientColumn;
    @FXML private TableColumn<InvoiceRow, String> issuedColumn;
    @FXML private TableColumn<InvoiceRow, String> totalColumn;
    @FXML private TableColumn<InvoiceRow, String> paidColumn;
    @FXML private TableColumn<InvoiceRow, String> balanceColumn;
    @FXML private TableColumn<InvoiceRow, String> statusColumn;
    @FXML private Label detailTitle;
    @FXML private Label detailMeta;
    @FXML private TableView<InvoiceItem> itemTable;
    @FXML private TableColumn<InvoiceItem, String> itemDescription;
    @FXML private TableColumn<InvoiceItem, String> itemQty;
    @FXML private TableColumn<InvoiceItem, String> itemPrice;
    @FXML private TableColumn<InvoiceItem, String> itemTotal;
    @FXML private TableView<Payment> paymentTable;
    @FXML private TableColumn<Payment, String> paymentDate;
    @FXML private TableColumn<Payment, String> paymentMethod;
    @FXML private TableColumn<Payment, String> paymentReference;
    @FXML private TableColumn<Payment, String> paymentAmount;
    @FXML private Label totalsLabel;
    @FXML private Button receiptButton;
    @FXML private Button paymentButton;
    @FXML private Button voidButton;
    @FXML private Button newButton;

    private AppContext context;
    private final List<InvoiceRow> allRows = new ArrayList<>();

    @Override
    public void init(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        if (context == null) {
            context = AppContext.get();
        }
        statusFilter.getItems().setAll("All statuses", "UNPAID", "PARTIAL", "PAID", "VOID");
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.valueProperty().addListener((obs, old, value) -> applyFilter());
        searchField.textProperty().addListener((obs, old, value) -> applyFilter());

        invoiceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        numberColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().invoice().invoiceNo()));
        patientColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().patientName()));
        issuedColumn.setCellValueFactory(data -> new SimpleStringProperty(
                Dates.display(data.getValue().invoice().issuedAt())));
        totalColumn.setCellValueFactory(data -> new SimpleStringProperty(
                Money.format(data.getValue().invoice().totalCents())));
        paidColumn.setCellValueFactory(data -> new SimpleStringProperty(
                Money.format(data.getValue().paidCents())));
        balanceColumn.setCellValueFactory(data -> new SimpleStringProperty(
                Money.format(data.getValue().balanceCents())));
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().invoice().status().name()));
        statusColumn.setCellFactory(Ui.badgeCell());
        invoiceTable.setPlaceholder(Ui.emptyState("No invoices", "Create the first invoice to start billing"));
        invoiceTable.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            showDetails(value);
            updateButtons();
        });
        invoiceTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                onReceipt();
            }
        });

        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        itemDescription.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().description()));
        itemQty.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().qty())));
        itemPrice.setCellValueFactory(data -> new SimpleStringProperty(
                Money.format(data.getValue().unitPriceCents())));
        itemTotal.setCellValueFactory(data -> new SimpleStringProperty(
                Money.format(data.getValue().lineTotalCents())));
        itemTable.setPlaceholder(Ui.emptyState("No items", "This invoice has no services"));

        paymentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        paymentDate.setCellValueFactory(data -> new SimpleStringProperty(
                Dates.display(data.getValue().paidAt())));
        paymentMethod.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().method().name()));
        paymentReference.setCellValueFactory(data -> new SimpleStringProperty(
                blank(data.getValue().reference())));
        paymentAmount.setCellValueFactory(data -> new SimpleStringProperty(
                Money.format(data.getValue().amountCents())));
        paymentTable.setPlaceholder(Ui.emptyState("No payments", "Record a payment to see it here"));

        boolean allowed = context.session().can(Permission.BILLING);
        for (Button button : List.of(receiptButton, paymentButton, voidButton, newButton)) {
            button.setVisible(allowed);
            button.setManaged(allowed);
        }
        load();
    }

    @Override
    public void refresh() {
        load();
    }

    private void load() {
        countLabel.setText("Loading…");
        Async.run(() -> {
            List<Invoice> invoices = context.billing().recentInvoices(300);
            Map<Long, Patient> patientCache = new HashMap<>();
            List<InvoiceRow> rows = new ArrayList<>(invoices.size());
            for (Invoice invoice : invoices) {
                Patient patient = patientCache.computeIfAbsent(invoice.patientId(),
                        id -> context.patients().basic(id));
                long paid = context.billing().paidTotal(invoice.id());
                rows.add(new InvoiceRow(invoice, patient, paid));
            }
            return rows;
        }, rows -> {
            allRows.clear();
            allRows.addAll(rows);
            applyFilter();
        }, throwable -> {
            countLabel.setText("Unable to load invoices");
            Dialogs.error(throwable);
        });
    }

    private void applyFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String status = statusFilter.getValue();
        List<InvoiceRow> filtered = allRows.stream()
                .filter(row -> query.isBlank() || row.matches(query))
                .filter(row -> status == null || "All statuses".equals(status)
                        || row.invoice().status().name().equals(status))
                .toList();
        invoiceTable.getItems().setAll(filtered);
        countLabel.setText(filtered.size() + " invoice" + (filtered.size() == 1 ? "" : "s"));

        long outstanding = allRows.stream()
                .filter(row -> row.invoice().status() != InvoiceStatus.VOID)
                .mapToLong(InvoiceRow::balanceCents)
                .sum();
        outstandingLabel.setText("Outstanding " + Money.format(outstanding));
        if (filtered.isEmpty()) {
            showDetails(null);
        }
        updateButtons();
    }

    private void showDetails(InvoiceRow row) {
        itemTable.getItems().clear();
        paymentTable.getItems().clear();
        totalsLabel.setText("");
        if (row == null) {
            detailTitle.setText("Invoice details");
            detailMeta.setText("Select an invoice to see its items and payments");
            return;
        }
        Invoice invoice = row.invoice();
        detailTitle.setText(invoice.invoiceNo());
        detailMeta.setText(row.patientName() + "   ·   issued " + Dates.display(invoice.issuedAt())
                + "   ·   " + invoice.status().name());
        totalsLabel.setText("Subtotal " + Money.format(invoice.subtotalCents())
                + "   ·   discount " + Money.format(invoice.discountCents())
                + "   ·   tax " + Money.format(invoice.taxCents())
                + "   ·   total " + Money.format(invoice.totalCents())
                + "   ·   paid " + Money.format(row.paidCents())
                + "   ·   balance " + Money.format(row.balanceCents()));

        long id = invoice.id();
        Async.run(() -> {
            List<InvoiceItem> items = context.billing().items(id);
            List<Payment> payments = context.billing().payments(id);
            return new Details(items, payments);
        }, details -> {
            InvoiceRow selected = invoiceTable.getSelectionModel().getSelectedItem();
            if (selected == null || selected.invoice().id() != id) {
                return;
            }
            itemTable.getItems().setAll(details.items());
            paymentTable.getItems().setAll(details.payments());
        });
    }

    private void updateButtons() {
        InvoiceRow selected = invoiceTable.getSelectionModel().getSelectedItem();
        boolean allowed = context.session().can(Permission.BILLING);
        boolean none = selected == null || !allowed;
        boolean settled = selected != null && (selected.invoice().status() == InvoiceStatus.PAID
                || selected.invoice().status() == InvoiceStatus.VOID);
        receiptButton.setDisable(none);
        paymentButton.setDisable(none || settled || selected.balanceCents() <= 0);
        voidButton.setDisable(none || selected.invoice().status() == InvoiceStatus.VOID);
        newButton.setDisable(!allowed);
    }

    @FXML
    private void onRefresh() {
        load();
    }

    @FXML
    private void onNew() {
        PatientPicker.chooseId(context, "New invoice").ifPresent(patientId ->
                Async.run(() -> context.billing().services(true), services -> {
                    if (services.isEmpty()) {
                        Dialogs.warn("The service catalogue is empty. Add services in Settings first.");
                        return;
                    }
                    InvoiceDialog.open(context, patientId, null, services, id -> {
                        Toasts.success("Invoice created");
                        load();
                    });
                }));
    }

    @FXML
    private void onPayment() {
        InvoiceRow selected = invoiceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        PaymentDialog.open(context, selected.invoice(), selected.paidCents(), paid -> {
            Toasts.success("Payment recorded");
            load();
        });
    }

    @FXML
    private void onReceipt() {
        InvoiceRow selected = invoiceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        long id = selected.invoice().id();
        Async.run(() -> context.billing().receiptPdf(id), Dialogs::openFile, Dialogs::error);
    }

    @FXML
    private void onVoid() {
        InvoiceRow selected = invoiceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (!Dialogs.confirm("Void invoice", "Void " + selected.invoice().invoiceNo()
                + "? The invoice stays on record but can no longer be paid.")) {
            return;
        }
        long id = selected.invoice().id();
        Async.run(() -> {
            context.billing().voidInvoice(id);
            return null;
        }, ignored -> {
            Toasts.success("Invoice voided");
            load();
        });
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private record Details(List<InvoiceItem> items, List<Payment> payments) {
    }

    private record InvoiceRow(Invoice invoice, Patient patient, long paidCents) {
        String patientName() {
            return patient == null ? "(unknown)" : patient.fullName().trim();
        }

        long balanceCents() {
            if (invoice.status() == InvoiceStatus.VOID) {
                return 0;
            }
            return Math.max(0, invoice.totalCents() - paidCents);
        }

        boolean matches(String query) {
            return invoice.invoiceNo().toLowerCase(Locale.ROOT).contains(query)
                    || patientName().toLowerCase(Locale.ROOT).contains(query);
        }
    }
}
