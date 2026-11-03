package org.amrosman.jsmartclinic.ui.patients;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.Problem;
import org.amrosman.jsmartclinic.domain.ProblemStatus;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Forms;
import org.amrosman.jsmartclinic.ui.components.Icd10Picker;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/** Add or edit an item on the patient's problem list. */
public final class ProblemDialog {

    private ProblemDialog() {
    }

    public static void open(AppContext context, long patientId, Problem existing, Consumer<Void> onSaved) {
        TextField code = Forms.text("ICD-10 code");
        TextField description = Forms.text("Problem description");
        DatePicker onset = Forms.date();
        ComboBox<ProblemStatus> status = Forms.combo(List.of(ProblemStatus.values()));
        status.setValue(ProblemStatus.ACTIVE);
        Button search = new Button("Search ICD-10");
        search.getStyleClass().add("secondary-button");
        search.setOnAction(e -> Icd10Picker.choose(context, code.getText()).ifPresent(chosen -> {
            code.setText(chosen.code());
            description.setText(chosen.description());
        }));

        if (existing != null) {
            code.setText(existing.icd10Code());
            description.setText(existing.description());
            onset.setValue(existing.onsetDate());
            status.setValue(existing.status());
        }
        Label error = Forms.errorLabel();

        GridPane grid = Forms.grid();
        Forms.row(grid, 0, "ICD-10 code", new HBox(8, code, search));
        Forms.row(grid, 1, "Description *", description);
        Forms.row(grid, 2, "Onset date", onset);
        Forms.row(grid, 3, "Status", status);

        Dialog<Void> dialog = Forms.dialog(existing == null ? "Add problem" : "Edit problem",
                "Problem list and chronic conditions", Forms.stack(grid, error));
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Forms.showError(error, null);
            if (description.getText().isBlank()) {
                Forms.showError(error, "Description is required");
                return;
            }
            String codeValue = blankToNull(code.getText());
            String descriptionValue = description.getText().trim();
            LocalDate onsetValue = onset.getValue();
            ProblemStatus statusValue = status.getValue();
            dialog.getDialogPane().lookupButton(saveType).setDisable(true);
            Async.run(() -> {
                if (existing == null) {
                    context.patients().addProblem(patientId, codeValue, descriptionValue, onsetValue, statusValue);
                } else {
                    context.patients().updateProblem(existing.id(), patientId, codeValue, descriptionValue,
                            onsetValue, statusValue);
                }
                return null;
            }, ignored -> {
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
