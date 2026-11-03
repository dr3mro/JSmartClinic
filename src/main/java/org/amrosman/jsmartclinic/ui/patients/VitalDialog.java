package org.amrosman.jsmartclinic.ui.patients;

import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.Vital;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Forms;

import java.time.Instant;
import java.util.function.Consumer;

/** Records a set of vitals, with live BMI calculation and range hints. */
public final class VitalDialog {

    private VitalDialog() {
    }

    public static void open(AppContext context, long patientId, Long encounterId, Consumer<Void> onSaved) {
        TextField systolic = Forms.text("120");
        TextField diastolic = Forms.text("80");
        TextField heartRate = Forms.text("72");
        TextField respRate = Forms.text("16");
        TextField temperature = Forms.text("36.8");
        TextField spo2 = Forms.text("98");
        TextField weight = Forms.text("70");
        TextField height = Forms.text("170");
        TextField glucose = Forms.text("90");

        Label bmiLabel = new Label("—");
        bmiLabel.getStyleClass().add("kpi-value");
        Label error = Forms.errorLabel();

        Runnable updateBmi = () -> {
            try {
                Double w = Forms.doubleOrNull(weight.getText());
                Double h = Forms.doubleOrNull(height.getText());
                if (w == null || h == null || h <= 0) {
                    bmiLabel.setText("—");
                } else {
                    double m = h / 100.0;
                    bmiLabel.setText(String.format(java.util.Locale.US, "%.1f", w / (m * m)));
                }
            } catch (NumberFormatException e) {
                bmiLabel.setText("—");
            }
        };
        weight.textProperty().addListener((obs, old, value) -> updateBmi.run());
        height.textProperty().addListener((obs, old, value) -> updateBmi.run());

        GridPane grid = Forms.grid();
        Forms.row(grid, 0, "Systolic (mmHg)", systolic);
        Forms.row(grid, 1, "Diastolic (mmHg)", diastolic);
        Forms.row(grid, 2, "Heart rate (bpm)", heartRate);
        Forms.row(grid, 3, "Resp. rate (/min)", respRate);
        Forms.row(grid, 4, "Temperature (°C)", temperature);
        Forms.row(grid, 5, "SpO₂ (%)", spo2);
        Forms.row(grid, 6, "Weight (kg)", weight);
        Forms.row(grid, 7, "Height (cm)", height);
        Forms.row(grid, 8, "Glucose (mg/dL)", glucose);
        Forms.row(grid, 9, "BMI", bmiLabel);

        Dialog<Void> dialog = Forms.dialog("Record vitals", "All fields are optional, but at least one is required",
                Forms.stack(grid, error));
        ButtonType saveType = new ButtonType("Save vitals", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Forms.showError(error, null);
            try {
                Integer systolicValue = Forms.intOrNull(systolic.getText());
                Integer diastolicValue = Forms.intOrNull(diastolic.getText());
                Integer heartRateValue = Forms.intOrNull(heartRate.getText());
                Integer respRateValue = Forms.intOrNull(respRate.getText());
                Double temperatureValue = Forms.doubleOrNull(temperature.getText());
                Integer spo2Value = Forms.intOrNull(spo2.getText());
                Double weightValue = Forms.doubleOrNull(weight.getText());
                Double heightValue = Forms.doubleOrNull(height.getText());
                Double glucoseValue = Forms.doubleOrNull(glucose.getText());

                if (systolicValue == null && diastolicValue == null && heartRateValue == null
                        && respRateValue == null && temperatureValue == null && spo2Value == null
                        && weightValue == null && heightValue == null && glucoseValue == null) {
                    Forms.showError(error, "Enter at least one vital sign");
                    return;
                }
                Vital vital = new Vital(0, patientId, encounterId, Instant.now(), systolicValue, diastolicValue,
                        heartRateValue, respRateValue, temperatureValue, spo2Value, weightValue, heightValue,
                        glucoseValue, null, null);
                dialog.getDialogPane().lookupButton(saveType).setDisable(true);
                Async.run(() -> context.encounters().addVital(vital), id -> {
                    dialog.setResult(null);
                    dialog.close();
                    if (onSaved != null) {
                        onSaved.accept(null);
                    }
                }, throwable -> {
                    dialog.getDialogPane().lookupButton(saveType).setDisable(false);
                    Forms.showFailure(error, throwable);
                });
            } catch (NumberFormatException e) {
                Forms.showError(error, "Enter valid numbers only");
            }
        });
        dialog.showAndWait();
    }
}
