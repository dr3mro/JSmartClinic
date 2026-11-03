package org.amrosman.jsmartclinic.ui.components;

import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/** Builders shared by the modal forms. */
public final class Forms {

    private Forms() {
    }

    public static <T> Dialog<T> dialog(String title, String header, Node content) {
        Dialog<T> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStyleClass().add("dialog-pane");
        ThemeAware.style(dialog.getDialogPane());
        dialog.getDialogPane().setMinWidth(460);
        return dialog;
    }

    public static GridPane grid() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("field-grid");
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(120);
        labels.setPrefWidth(130);
        labels.setHalignment(HPos.LEFT);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        values.setFillWidth(true);
        grid.getColumnConstraints().addAll(labels, values);
        return grid;
    }

    public static void row(GridPane grid, int row, String label, Node field) {
        Label caption = new Label(label);
        caption.getStyleClass().add("form-label");
        grid.add(caption, 0, row);
        grid.add(field, 1, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
        if (field instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
    }

    public static TextField text(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("form-field");
        return field;
    }

    public static TextArea area(String prompt) {
        TextArea area = new TextArea();
        area.setPromptText(prompt);
        area.setWrapText(true);
        area.getStyleClass().add("form-field");
        area.setPrefRowCount(3);
        return area;
    }

    public static <T> ComboBox<T> combo(List<T> items) {
        ComboBox<T> combo = new ComboBox<>();
        combo.getItems().setAll(items);
        combo.getStyleClass().add("form-field");
        combo.setMaxWidth(Double.MAX_VALUE);
        return combo;
    }

    public static DatePicker date() {
        DatePicker picker = new DatePicker();
        picker.getStyleClass().add("form-field");
        picker.setMaxWidth(Double.MAX_VALUE);
        return picker;
    }

    public static Label errorLabel() {
        Label label = new Label();
        label.getStyleClass().add("error-text");
        label.setWrapText(true);
        label.setVisible(false);
        label.setManaged(false);
        return label;
    }

    public static void showError(Label label, String message) {
        label.setText(message == null ? "" : message);
        label.setVisible(message != null && !message.isBlank());
        label.setManaged(label.isVisible());
    }

    public static void showFailure(Label label, Throwable error) {
        showError(label, error == null ? null : error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage());
    }

    public static VBox stack(Node... nodes) {
        VBox box = new VBox(10, nodes);
        return box;
    }

    public static boolean validPhone(String value) {
        return value == null || value.isBlank() || value.trim().matches("^[+()\\-\\s0-9]{6,25}$");
    }

    public static boolean validEmail(String value) {
        return value == null || value.isBlank()
                || value.trim().matches("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");
    }

    public static Integer intOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.valueOf(value.trim());
    }

    public static Double doubleOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Double.valueOf(value.trim());
    }

    /** Small internal bridge to avoid a direct dependency cycle with the theme manager. */
    private static final class ThemeAware {
        static void style(javafx.scene.control.DialogPane pane) {
            org.amrosman.jsmartclinic.ui.ThemeManager.applyToDialog(pane);
        }
    }
}
