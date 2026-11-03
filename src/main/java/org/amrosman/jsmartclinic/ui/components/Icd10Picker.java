package org.amrosman.jsmartclinic.ui.components;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.service.Icd10Service;

import java.util.Optional;

/** Small ICD-10-CM search dialog backed by the bundled catalogue. */
public final class Icd10Picker {

    private Icd10Picker() {
    }

    public static Optional<Icd10Service.IcdCode> choose(AppContext context, String initialQuery) {
        Dialog<Icd10Service.IcdCode> dialog = new Dialog<>();
        dialog.setTitle("Search ICD-10");
        dialog.setHeaderText("Find a diagnosis code");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().getStyleClass().add("dialog-pane");
        org.amrosman.jsmartclinic.ui.ThemeManager.applyToDialog(dialog.getDialogPane());

        TextField search = new TextField(initialQuery == null ? "" : initialQuery);
        search.setPromptText("Code or description (e.g. E11 or diabetes)");
        search.getStyleClass().add("form-field");
        ListView<Icd10Service.IcdCode> list = new ListView<>();
        list.setPrefHeight(300);
        Label hint = new Label("Type at least two characters to search.");
        hint.getStyleClass().add("muted");
        VBox box = new VBox(10, search, list, hint);
        box.setPadding(new Insets(6, 4, 0, 4));
        box.setPrefWidth(520);
        HBox.setHgrow(search, Priority.ALWAYS);
        dialog.getDialogPane().setContent(box);

        Runnable load = () -> {
            String query = search.getText();
            if (query != null && query.trim().length() == 1) {
                return;
            }
            Async.run(() -> context.icd10().search(query, 80), codes -> {
                list.getItems().setAll(codes);
                hint.setText(codes.isEmpty() ? "No matching codes" : codes.size() + " result(s)");
            }, throwable -> hint.setText("Search failed: " + throwable.getMessage()));
        };
        search.textProperty().addListener((obs, old, value) -> load.run());
        list.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) {
                dialog.setResult(list.getSelectionModel().getSelectedItem());
                dialog.close();
            }
        });
        javafx.application.Platform.runLater(() -> {
            load.run();
            search.requestFocus();
        });
        dialog.setResultConverter(button -> button == ButtonType.OK ? list.getSelectionModel().getSelectedItem() : null);
        return Optional.ofNullable(dialog.showAndWait().orElse(null));
    }
}
