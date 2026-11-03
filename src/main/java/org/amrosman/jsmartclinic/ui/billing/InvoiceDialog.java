package org.amrosman.jsmartclinic.ui.billing;

import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.BillableService;
import org.amrosman.jsmartclinic.domain.InvoiceItem;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Forms;
import org.amrosman.jsmartclinic.util.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Build an invoice from the services catalogue with a discount and tax preview. */
public final class InvoiceDialog {

    private InvoiceDialog() {
    }

    public record Draft(long serviceId, String description, int qty, long unitPriceCents) {
    }

    public static void open(AppContext context, long patientId, Long encounterId, List<BillableService> services,
                            Consumer<Long> onCreated) {
        TableView<Draft> items = new TableView<>();
        items.setPrefHeight(220);
        items.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Draft, String> description = new TableColumn<>("Service");
        description.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().description()));
        TableColumn<Draft, String> qty = new TableColumn<>("Qty");
        qty.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().qty())));
        TableColumn<Draft, String> price = new TableColumn<>("Unit price");
        price.setCellValueFactory(data -> new SimpleStringProperty(Money.format(data.getValue().unitPriceCents())));
        TableColumn<Draft, String> total = new TableColumn<>("Amount");
        total.setCellValueFactory(data -> new SimpleStringProperty(Money.format(
                (long) data.getValue().qty() * data.getValue().unitPriceCents())));
        items.getColumns().addAll(description, qty, price, total);

        ListView<BillableService> available = new ListView<>();
        available.getItems().setAll(services);
        available.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        available.setPrefHeight(220);
        available.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(BillableService item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item.name() + "   ·   " + Money.format(item.priceCents()));
            }
        });

        TextField discount = Forms.text("0.00");
        Label subtotalLabel = new Label();
        Label taxLabel = new Label();
        Label totalLabel = new Label();
        totalLabel.getStyleClass().add("kpi-value");
        Label error = Forms.errorLabel();
        int taxRate = context.settings().taxRate();

        Runnable recalc = () -> {
            long subtotal = items.getItems().stream()
                    .mapToLong(i -> (long) i.qty() * i.unitPriceCents()).sum();
            long discountCents;
            try {
                discountCents = Money.parseToCents(discount.getText());
            } catch (RuntimeException e) {
                discountCents = 0;
            }
            discountCents = Math.max(0, Math.min(subtotal, discountCents));
            long tax = Math.round((subtotal - discountCents) * (taxRate / 100.0));
            subtotalLabel.setText("Subtotal  " + Money.format(subtotal));
            taxLabel.setText("Tax (" + taxRate + "%)  " + Money.format(tax));
            totalLabel.setText("Total  " + Money.format(subtotal - discountCents + tax));
        };
        discount.textProperty().addListener((obs, old, value) -> recalc.run());
        items.getItems().addListener((javafx.collections.ListChangeListener<Draft>) change -> recalc.run());

        Button add = new Button("Add selected");
        add.getStyleClass().add("secondary-button");
        add.setOnAction(e -> {
            for (BillableService service : available.getSelectionModel().getSelectedItems()) {
                items.getItems().add(new Draft(service.id(), service.name(), 1, service.priceCents()));
            }
        });
        Button remove = new Button("Remove");
        remove.getStyleClass().add("danger-button");
        remove.setOnAction(e -> {
            Draft selected = items.getSelectionModel().getSelectedItem();
            if (selected != null) {
                items.getItems().remove(selected);
            }
        });

        HBox columns = new HBox(14,
                new VBox(6, new Label("Services catalogue"), available),
                new VBox(6, new HBox(8, add, remove), items));
        HBox.setHgrow(columns.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(columns.getChildren().get(1), Priority.ALWAYS);
        VBox content = new VBox(10, columns,
                new HBox(10, new Label("Discount"), discount, subtotalLabel, taxLabel, totalLabel),
                error);
        content.setPadding(new Insets(4));
        content.setPrefWidth(780);

        Dialog<Void> dialog = Forms.dialog("New invoice", "Select services, then apply a discount if needed", content);
        ButtonType createType = new ButtonType("Create invoice", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(createType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Forms.showError(error, null);
            if (items.getItems().isEmpty()) {
                Forms.showError(error, "Add at least one service");
                return;
            }
            long discountCents = Math.max(0, Money.parseToCents(discount.getText()));
            List<InvoiceItem> invoiceItems = new ArrayList<>();
            for (Draft draft : items.getItems()) {
                invoiceItems.add(new InvoiceItem(0, 0, draft.serviceId(), draft.description(), draft.qty(),
                        draft.unitPriceCents()));
            }
            dialog.getDialogPane().lookupButton(createType).setDisable(true);
            Async.run(() -> context.billing().createInvoice(patientId, encounterId, invoiceItems, discountCents),
                    id -> {
                        dialog.setResult(null);
                        dialog.close();
                        if (onCreated != null) {
                            onCreated.accept(id);
                        }
                    }, throwable -> {
                        dialog.getDialogPane().lookupButton(createType).setDisable(false);
                        Forms.showFailure(error, throwable);
                    });
        });
        recalc.run();
        dialog.showAndWait();
    }
}
