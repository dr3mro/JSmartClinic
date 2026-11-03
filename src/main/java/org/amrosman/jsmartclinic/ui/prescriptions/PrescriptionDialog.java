package org.amrosman.jsmartclinic.ui.prescriptions;

import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.Prescription;
import org.amrosman.jsmartclinic.domain.PrescriptionItem;
import org.amrosman.jsmartclinic.service.DrugCatalog;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Dialogs;
import org.amrosman.jsmartclinic.ui.components.Forms;

import java.util.List;
import java.util.function.Consumer;

/** Multi-item prescription builder with basic allergy cross-checks. */
public final class PrescriptionDialog {

    private PrescriptionDialog() {
    }

    public static void open(AppContext context, long patientId, Long encounterId, Consumer<Long> onCreated) {
        ComboBox<DrugCatalog.Drug> drug = new ComboBox<>();
        drug.setEditable(true);
        drug.getStyleClass().add("form-field");
        drug.setMaxWidth(Double.MAX_VALUE);
        drug.setPromptText("Start typing a drug name");
        drug.getItems().setAll(context.drugs().search("", 12));
        drug.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(DrugCatalog.Drug item) {
                return item == null ? "" : item.name();
            }

            @Override
            public DrugCatalog.Drug fromString(String string) {
                return string == null ? null : new DrugCatalog.Drug(string, "", "");
            }
        });
        drug.getEditor().textProperty().addListener((obs, old, value) -> {
            if (drug.getSelectionModel().getSelectedItem() == null
                    || !drug.getSelectionModel().getSelectedItem().name().equals(value)) {
                List<DrugCatalog.Drug> matches = context.drugs().search(value, 10);
                drug.getItems().setAll(matches);
                if (!matches.isEmpty() && value != null && !value.isBlank()) {
                    drug.show();
                }
            }
        });
        drug.valueProperty().addListener((obs, old, value) -> {
            if (value != null) {
                drug.getEditor().setText(value.name());
            }
        });

        TextField strength = Forms.text("e.g. 500 mg");
        TextField form = Forms.text("e.g. tablet");
        TextField dose = Forms.text("e.g. 1 tablet");
        TextField frequency = Forms.text("e.g. twice daily");
        TextField duration = Forms.text("e.g. 7 days");
        TextField quantity = Forms.text("e.g. 14");
        TextField instructions = Forms.text("e.g. after meals");
        Label error = Forms.errorLabel();

        TableView<PrescriptionItem> items = new TableView<>();
        items.setPrefHeight(200);
        items.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<PrescriptionItem, String> drugColumn = new TableColumn<>("Drug");
        drugColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().drugName()
                + (data.getValue().strength() == null ? "" : " " + data.getValue().strength())));
        TableColumn<PrescriptionItem, String> doseColumn = new TableColumn<>("Dose");
        doseColumn.setCellValueFactory(data -> new SimpleStringProperty(nz(data.getValue().dose())));
        TableColumn<PrescriptionItem, String> frequencyColumn = new TableColumn<>("Frequency");
        frequencyColumn.setCellValueFactory(data -> new SimpleStringProperty(nz(data.getValue().frequency())));
        TableColumn<PrescriptionItem, String> durationColumn = new TableColumn<>("Duration");
        durationColumn.setCellValueFactory(data -> new SimpleStringProperty(nz(data.getValue().duration())));
        TableColumn<PrescriptionItem, String> quantityColumn = new TableColumn<>("Qty");
        quantityColumn.setCellValueFactory(data -> new SimpleStringProperty(nz(data.getValue().quantity())));
        items.getColumns().addAll(drugColumn, doseColumn, frequencyColumn, durationColumn, quantityColumn);

        Label warning = new Label();
        warning.getStyleClass().add("warn-banner");
        warning.setWrapText(true);
        warning.setVisible(false);
        warning.setManaged(false);

        Runnable refreshWarnings = () -> {
            List<PrescriptionItem> snapshot = List.copyOf(items.getItems());
            if (snapshot.isEmpty()) {
                warning.setVisible(false);
                warning.setManaged(false);
                return;
            }
            Async.run(() -> context.prescriptions().allergyWarningText(patientId, snapshot), text -> {
                warning.setText(text);
                warning.setVisible(text != null);
                warning.setManaged(text != null);
            }, ignored -> {
                warning.setVisible(false);
                warning.setManaged(false);
            });
        };
        items.getItems().addListener((javafx.collections.ListChangeListener<PrescriptionItem>) change ->
                refreshWarnings.run());

        Button add = new Button("Add medication");
        add.getStyleClass().add("secondary-button");
        add.setOnAction(e -> {
            Forms.showError(error, null);
            String name = drug.getEditor().getText() == null ? "" : drug.getEditor().getText().trim();
            if (name.isBlank()) {
                Forms.showError(error, "Enter or choose a drug");
                return;
            }
            items.getItems().add(new PrescriptionItem(0, 0, name, blankToNull(strength.getText()),
                    blankToNull(form.getText()), blankToNull(dose.getText()), blankToNull(frequency.getText()),
                    blankToNull(duration.getText()), blankToNull(quantity.getText()),
                    blankToNull(instructions.getText())));
            drug.getEditor().clear();
            drug.setValue(null);
            strength.clear();
            form.clear();
            dose.clear();
            frequency.clear();
            duration.clear();
            quantity.clear();
            instructions.clear();
            refreshWarnings.run();
        });
        Button remove = new Button("Remove");
        remove.getStyleClass().add("danger-button");
        remove.setOnAction(e -> items.getItems().remove(items.getSelectionModel().getSelectedItem()));

        GridPane entry = Forms.grid();
        Forms.row(entry, 0, "Drug *", drug);
        Forms.row(entry, 1, "Strength", strength);
        Forms.row(entry, 2, "Form", form);
        Forms.row(entry, 3, "Dose", dose);
        Forms.row(entry, 4, "Frequency", frequency);
        Forms.row(entry, 5, "Duration", duration);
        Forms.row(entry, 6, "Quantity", quantity);
        Forms.row(entry, 7, "Instructions", instructions);

        TextArea notes = Forms.area("Notes for the pharmacist or patient");

        VBox content = new VBox(12,
                entry,
                new HBox(8, add, remove),
                items,
                warning,
                new Label("Notes"), notes,
                error);
        content.setPadding(new Insets(4));
        content.setPrefWidth(720);
        HBox.setHgrow(items, Priority.ALWAYS);

        Dialog<Void> dialog = Forms.dialog("New prescription", "Build the prescription, then review allergy warnings",
                content);
        ButtonType saveType = new ButtonType("Issue prescription", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Forms.showError(error, null);
            if (items.getItems().isEmpty()) {
                Forms.showError(error, "Add at least one medication");
                return;
            }
            List<PrescriptionItem> snapshot = List.copyOf(items.getItems());
            Async.run(() -> context.prescriptions().allergyWarningText(patientId, snapshot), text -> {
                if (text != null && !Dialogs.confirm("Allergy warning",
                        text + "\n\nIssue this prescription anyway?")) {
                    return;
                }
                dialog.getDialogPane().lookupButton(saveType).setDisable(true);
                Async.run(() -> context.prescriptions().create(patientId, encounterId, notes.getText(), snapshot),
                        id -> {
                            dialog.setResult(null);
                            dialog.close();
                            if (onCreated != null) {
                                onCreated.accept(id);
                            }
                        }, throwable -> {
                            dialog.getDialogPane().lookupButton(saveType).setDisable(false);
                            Forms.showFailure(error, throwable);
                        });
            }, throwable -> Forms.showFailure(error, throwable));
        });
        dialog.showAndWait();
    }

    /** Read-only preview of an existing prescription. */
    public static void view(AppContext context, Prescription prescription) {
        TableView<PrescriptionItem> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<PrescriptionItem, String> drugColumn = new TableColumn<>("Drug");
        drugColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().drugName()
                + (data.getValue().strength() == null ? "" : " " + data.getValue().strength())));
        TableColumn<PrescriptionItem, String> doseColumn = new TableColumn<>("Dose / frequency");
        doseColumn.setCellValueFactory(data -> new SimpleStringProperty(
                nz(data.getValue().dose()) + " · " + nz(data.getValue().frequency())));
        TableColumn<PrescriptionItem, String> durationColumn = new TableColumn<>("Duration");
        durationColumn.setCellValueFactory(data -> new SimpleStringProperty(nz(data.getValue().duration())));
        TableColumn<PrescriptionItem, String> instructionsColumn = new TableColumn<>("Instructions");
        instructionsColumn.setCellValueFactory(data -> new SimpleStringProperty(nz(data.getValue().instructions())));
        table.getColumns().addAll(drugColumn, doseColumn, durationColumn, instructionsColumn);
        table.setPrefSize(680, 300);

        Label notes = new Label(prescription.notes() == null ? "" : prescription.notes());
        notes.setWrapText(true);
        notes.getStyleClass().add("muted");
        Dialog<Void> dialog = Forms.dialog("Prescription #" + prescription.id(),
                org.amrosman.jsmartclinic.util.Dates.display(prescription.issuedAt()), new VBox(10, table, notes));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Async.run(() -> context.prescriptions().items(prescription.id()),
                list -> table.getItems().setAll(list));
        dialog.showAndWait();
    }

    private static String nz(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
