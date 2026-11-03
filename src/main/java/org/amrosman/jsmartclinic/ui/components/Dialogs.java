package org.amrosman.jsmartclinic.ui.components;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.nio.file.Path;
import java.util.Optional;

/** Consistent dialogs and file opening. */
public final class Dialogs {

    private static final Logger log = LoggerFactory.getLogger(Dialogs.class);

    private Dialogs() {
    }

    public static void info(String message) {
        show(Alert.AlertType.INFORMATION, "JSmartClinic", message);
    }

    public static void warn(String message) {
        show(Alert.AlertType.WARNING, "JSmartClinic", message);
    }

    public static void error(String message) {
        show(Alert.AlertType.ERROR, "Something went wrong", message);
    }

    public static void error(Throwable throwable) {
        String message = throwable.getMessage();
        show(Alert.AlertType.ERROR, "Something went wrong",
                message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message);
    }

    public static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(title);
        alert.setHeaderText(null);
        style(alert);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static void openFile(Path path) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(path.toFile());
            } else {
                info("File saved to:\n" + path);
            }
        } catch (Exception e) {
            log.warn("Unable to open file {}", path, e);
            info("File saved to:\n" + path);
        }
    }

    private static void show(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        style(alert);
        alert.showAndWait();
    }

    private static void style(Alert alert) {
        alert.getDialogPane().getStyleClass().add("dialog-pane");
        org.amrosman.jsmartclinic.ui.ThemeManager.applyToDialog(alert.getDialogPane());
    }
}
