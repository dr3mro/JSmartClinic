package org.amrosman.jsmartclinic.ui.billing;

import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.Invoice;
import org.amrosman.jsmartclinic.domain.PaymentMethod;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Forms;
import org.amrosman.jsmartclinic.util.Money;

import java.util.List;
import java.util.function.Consumer;

/** Records a full or partial payment against an invoice. */
public final class PaymentDialog {

    private PaymentDialog() {
    }

    public static void open(AppContext context, Invoice invoice, long paidCents, Consumer<Long> onSaved) {
        long balance = invoice.totalCents() - paidCents;
        TextField amount = Forms.text(Money.format(balance));
        amount.setText(String.format(java.util.Locale.US, "%.2f", balance / 100.0));
        ComboBox<PaymentMethod> method = Forms.combo(List.of(PaymentMethod.values()));
        method.setValue(PaymentMethod.CASH);
        TextField reference = Forms.text("Card / transfer reference");
        Label outstanding = new Label("Outstanding balance: " + Money.format(balance));
        outstanding.getStyleClass().add("muted");
        Label error = Forms.errorLabel();

        GridPane grid = Forms.grid();
        Forms.row(grid, 0, "Invoice", new Label(invoice.invoiceNo()));
        Forms.row(grid, 1, "Balance", outstanding);
        Forms.row(grid, 2, "Amount", amount);
        Forms.row(grid, 3, "Method", method);
        Forms.row(grid, 4, "Reference", reference);

        Dialog<Void> dialog = Forms.dialog("Record payment", invoice.invoiceNo(), Forms.stack(grid, error));
        ButtonType saveType = new ButtonType("Record payment", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Forms.showError(error, null);
            long cents;
            try {
                cents = Money.parseToCents(amount.getText());
            } catch (RuntimeException e) {
                Forms.showError(error, "Enter a valid amount");
                return;
            }
            if (cents <= 0) {
                Forms.showError(error, "Amount must be greater than zero");
                return;
            }
            dialog.getDialogPane().lookupButton(saveType).setDisable(true);
            Async.run(() -> {
                context.billing().recordPayment(invoice.id(), cents, method.getValue(), reference.getText());
                return cents;
            }, paid -> {
                dialog.setResult(null);
                dialog.close();
                if (onSaved != null) {
                    onSaved.accept(paid);
                }
            }, throwable -> {
                dialog.getDialogPane().lookupButton(saveType).setDisable(false);
                Forms.showFailure(error, throwable);
            });
        });
        dialog.showAndWait();
    }
}
