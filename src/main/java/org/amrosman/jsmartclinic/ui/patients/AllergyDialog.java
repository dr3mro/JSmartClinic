package org.amrosman.jsmartclinic.ui.patients;

import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.Severity;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Forms;

import java.util.List;
import java.util.function.Consumer;

/** Records a new allergy for a patient. */
public final class AllergyDialog {

    private AllergyDialog() {
    }

    public static void open(AppContext context, long patientId, Consumer<Void> onSaved) {
        TextField substance = Forms.text("e.g. Penicillin");
        TextField reaction = Forms.text("e.g. Rash, anaphylaxis");
        ComboBox<Severity> severity = Forms.combo(List.of(Severity.values()));
        severity.setValue(Severity.MODERATE);
        Label error = Forms.errorLabel();

        GridPane grid = Forms.grid();
        Forms.row(grid, 0, "Substance *", substance);
        Forms.row(grid, 1, "Reaction", reaction);
        Forms.row(grid, 2, "Severity", severity);

        Dialog<Void> dialog = Forms.dialog("Record allergy", "Allergies appear in a red banner on the chart",
                Forms.stack(grid, error));
        ButtonType saveType = new ButtonType("Save allergy", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Forms.showError(error, null);
            if (substance.getText().isBlank()) {
                Forms.showError(error, "Substance is required");
                return;
            }
            dialog.getDialogPane().lookupButton(saveType).setDisable(true);
            Async.run(() -> context.patients().addAllergy(patientId, substance.getText(), reaction.getText(),
                            severity.getValue()), allergy -> {
                        dialog.setResult(null);
                        dialog.close();
                        if (onSaved != null) {
                            onSaved.accept(null);
                        }
                    }, throwable -> {
                        dialog.getDialogPane().lookupButton(saveType).setDisable(false);
                        Forms.showFailure(error, throwable);
                    });
        });
        dialog.showAndWait();
    }
}
