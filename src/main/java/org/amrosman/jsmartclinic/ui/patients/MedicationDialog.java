package org.amrosman.jsmartclinic.ui.patients;

import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.Medication;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Forms;

import java.time.LocalDate;
import java.util.function.Consumer;

/** Add or edit a medication on the patient's current medication list. */
public final class MedicationDialog {

    private MedicationDialog() {
    }

    public static void open(AppContext context, long patientId, Medication existing, Consumer<Void> onSaved) {
        TextField name = Forms.text("Generic drug name");
        TextField dose = Forms.text("e.g. 500 mg");
        ComboBox<String> route = Forms.combo(java.util.List.of("Oral", "IV", "IM", "Subcutaneous", "Topical",
                "Inhaled", "Rectal", "Ophthalmic"));
        route.setEditable(true);
        TextField frequency = Forms.text("e.g. twice daily");
        DatePicker start = Forms.date();
        DatePicker end = Forms.date();
        CheckBox active = new CheckBox("Active medication");
        active.setSelected(true);

        if (existing != null) {
            name.setText(existing.name());
            dose.setText(existing.dose());
            route.setValue(existing.route());
            frequency.setText(existing.frequency());
            start.setValue(existing.startDate());
            end.setValue(existing.endDate());
            active.setSelected(existing.active());
        } else {
            start.setValue(LocalDate.now());
        }
        Label error = Forms.errorLabel();

        GridPane grid = Forms.grid();
        Forms.row(grid, 0, "Medication *", name);
        Forms.row(grid, 1, "Dose", dose);
        Forms.row(grid, 2, "Route", route);
        Forms.row(grid, 3, "Frequency", frequency);
        Forms.row(grid, 4, "Start date", start);
        Forms.row(grid, 5, "End date", end);
        Forms.row(grid, 6, "Status", active);

        Dialog<Void> dialog = Forms.dialog(existing == null ? "Add medication" : "Edit medication",
                "Current and historical medication list", Forms.stack(grid, error));
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Forms.showError(error, null);
            if (name.getText().isBlank()) {
                Forms.showError(error, "Medication name is required");
                return;
            }
            if (start.getValue() != null && end.getValue() != null && end.getValue().isBefore(start.getValue())) {
                Forms.showError(error, "End date cannot be before the start date");
                return;
            }
            String nameValue = name.getText().trim();
            String doseValue = blankToNull(dose.getText());
            String routeValue = blankToNull(route.getValue());
            String frequencyValue = blankToNull(frequency.getText());
            LocalDate startValue = start.getValue();
            LocalDate endValue = end.getValue();
            boolean activeValue = active.isSelected();
            dialog.getDialogPane().lookupButton(saveType).setDisable(true);
            Async.run(() -> {
                if (existing == null) {
                    context.patients().addMedication(patientId, nameValue, doseValue, routeValue, frequencyValue,
                            startValue, endValue, activeValue);
                } else {
                    context.patients().updateMedication(existing.id(), patientId, nameValue, doseValue, routeValue,
                            frequencyValue, startValue, endValue, activeValue);
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
