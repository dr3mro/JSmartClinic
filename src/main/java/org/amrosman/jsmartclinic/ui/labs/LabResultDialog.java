package org.amrosman.jsmartclinic.ui.labs;

import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.LabFlag;
import org.amrosman.jsmartclinic.repository.LabRepository;
import org.amrosman.jsmartclinic.service.LabService;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Forms;

import java.util.function.Consumer;

/** Enter or correct a lab result, with a live flag preview. */
public final class LabResultDialog {

    private LabResultDialog() {
    }

    public static void open(AppContext context, LabRepository.LabRow row, Consumer<LabFlag> onSaved) {
        TextField value = Forms.text("Numeric result");
        if (row.valueNum() != null) {
            value.setText(String.valueOf(row.valueNum()));
        }
        TextField text = Forms.text("Or a text result (e.g. Positive)");
        if (row.valueText() != null) {
            text.setText(row.valueText());
        }
        Label range = new Label(reference(row));
        range.getStyleClass().add("muted");
        Label flagLabel = new Label(row.flag() == null ? "Not resulted" : row.flag().name());
        flagLabel.getStyleClass().addAll("badge", "badge-" + (row.flag() == null ? "draft"
                : row.flag().name().toLowerCase(java.util.Locale.ROOT)));
        Label error = Forms.errorLabel();

        Runnable preview = () -> {
            try {
                Double numeric = Forms.doubleOrNull(value.getText());
                LabFlag flag = LabService.flagFor(row.refLow(), row.refHigh(), row.critLow(),
                        row.critHigh(), numeric);
                flagLabel.setText(flag == null ? (text.getText().isBlank() ? "Not resulted" : "TEXT") : flag.name());
                flagLabel.getStyleClass().setAll("badge",
                        "badge-" + (flag == null ? "draft" : flag.name().toLowerCase(java.util.Locale.ROOT)));
            } catch (NumberFormatException e) {
                flagLabel.setText("Invalid number");
            }
        };
        value.textProperty().addListener((obs, old, v) -> preview.run());
        text.textProperty().addListener((obs, old, v) -> preview.run());

        GridPane grid = Forms.grid();
        Forms.row(grid, 0, "Test", new Label(row.testName()));
        Forms.row(grid, 1, "Reference", range);
        Forms.row(grid, 2, "Numeric result", value);
        Forms.row(grid, 3, "Text result", text);
        Forms.row(grid, 4, "Calculated flag", flagLabel);

        Dialog<Void> dialog = Forms.dialog("Lab result", row.testName(), Forms.stack(grid, error));
        ButtonType saveType = new ButtonType("Save result", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Forms.showError(error, null);
            try {
                Double numeric = Forms.doubleOrNull(value.getText());
                String textValue = text.getText() == null || text.getText().isBlank() ? null : text.getText().trim();
                if (numeric == null && textValue == null) {
                    Forms.showError(error, "Enter a numeric or text result");
                    return;
                }
                if (row.resultId() == null) {
                    Forms.showError(error, "This order has no result line to update");
                    return;
                }
                dialog.getDialogPane().lookupButton(saveType).setDisable(true);
                Async.run(() -> context.labs().saveResult(row.orderId(), row.patientId(), row.resultId(),
                                row.testId(), numeric, textValue), flag -> {
                            dialog.setResult(null);
                            dialog.close();
                            if (onSaved != null) {
                                onSaved.accept(flag);
                            }
                        }, throwable -> {
                            dialog.getDialogPane().lookupButton(saveType).setDisable(false);
                            Forms.showFailure(error, throwable);
                        });
            } catch (NumberFormatException e) {
                Forms.showError(error, "Numeric result must be a number");
            }
        });
        dialog.showAndWait();
    }

    public static String reference(LabRepository.LabRow row) {
        if (row.refLow() == null && row.refHigh() == null) {
            return "—";
        }
        String low = row.refLow() == null ? "" : trim(row.refLow());
        String high = row.refHigh() == null ? "" : trim(row.refHigh());
        return low + " – " + high + (row.unit() == null ? "" : " " + row.unit());
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
