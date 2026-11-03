package org.amrosman.jsmartclinic.ui.labs;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.LabTest;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Forms;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Choose one or more lab tests and place an order. */
public final class LabOrderDialog {

    private LabOrderDialog() {
    }

    public static void open(AppContext context, long patientId, Long encounterId, Consumer<Long> onOrdered) {
        TextField search = Forms.text("Filter tests");
        VBox list = new VBox(4);
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(360);
        Map<Long, CheckBox> boxes = new LinkedHashMap<>();
        Label error = Forms.errorLabel();
        Label hint = new Label("Select the tests to order.");
        hint.getStyleClass().add("muted");

        List<LabTest> all = context.labs().tests();
        Runnable render = () -> {
            String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(java.util.Locale.ROOT);
            list.getChildren().clear();
            for (LabTest test : all) {
                if (!query.isBlank() && !(test.name() + " " + test.code() + " " + test.category())
                        .toLowerCase(java.util.Locale.ROOT).contains(query)) {
                    continue;
                }
                CheckBox box = boxes.computeIfAbsent(test.id(), id -> new CheckBox());
                box.setText(test.name() + (test.code() == null ? "" : "  (" + test.code() + ")")
                        + (test.unit() == null ? "" : "   · " + test.unit()));
                box.getStyleClass().add("form-label");
                list.getChildren().add(box);
            }
            if (list.getChildren().isEmpty()) {
                Label none = new Label("No tests match this filter");
                none.getStyleClass().add("muted");
                list.getChildren().add(none);
            }
        };
        search.textProperty().addListener((obs, old, value) -> render.run());
        render.run();

        VBox content = new VBox(10, new HBox(10, search, hint), scroll, error);
        content.setPadding(new Insets(4));
        HBox.setHgrow(search, Priority.ALWAYS);

        Dialog<Void> dialog = Forms.dialog("Order laboratory tests", "Orders appear in the patient chart and lab queue",
                content);
        ButtonType orderType = new ButtonType("Place order", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(orderType, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(orderType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            List<Long> selected = boxes.entrySet().stream()
                    .filter(entry -> entry.getValue().isSelected())
                    .map(Map.Entry::getKey).toList();
            Forms.showError(error, null);
            if (selected.isEmpty()) {
                Forms.showError(error, "Select at least one test");
                return;
            }
            dialog.getDialogPane().lookupButton(orderType).setDisable(true);
            Async.run(() -> context.labs().order(patientId, encounterId, selected), orderId -> {
                dialog.setResult(null);
                dialog.close();
                if (onOrdered != null) {
                    onOrdered.accept(orderId);
                }
            }, throwable -> {
                dialog.getDialogPane().lookupButton(orderType).setDisable(false);
                Forms.showFailure(error, throwable);
            });
        });
        dialog.showAndWait();
    }
}
