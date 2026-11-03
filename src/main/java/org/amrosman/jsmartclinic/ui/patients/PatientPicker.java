package org.amrosman.jsmartclinic.ui.patients;

import javafx.animation.PauseTransition;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Forms;
import org.amrosman.jsmartclinic.ui.components.Ui;
import org.amrosman.jsmartclinic.util.Dates;

import java.util.Optional;

/** Searchable patient selector used when a flow starts outside the chart. */
public final class PatientPicker {

    private PatientPicker() {
    }

    public static Optional<Patient> choose(AppContext context, String title) {
        TextField search = Forms.text("Search by name, MRN or phone");
        ListView<Patient> list = new ListView<>();
        list.setPrefSize(520, 320);
        list.setCellFactory(view -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Patient item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(item.fullName().trim() + "  ·  " + item.mrn() + "  ·  "
                        + item.age() + " yrs  ·  born " + Dates.displayDate(item.dateOfBirth()));
            }
        });
        list.setPlaceholder(Ui.emptyState("No matching patients", "Try a different name or MRN"));
        VBox content = new VBox(10, search, list);
        content.setPrefWidth(540);

        Dialog<Patient> dialog = Forms.dialog(title, "Choose a patient to continue", content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? list.getSelectionModel().getSelectedItem() : null);

        PauseTransition debounce = new PauseTransition(Duration.millis(220));
        Runnable load = () -> {
            String query = search.getText() == null ? "" : search.getText().trim();
            Async.run(() -> context.patients().search(query, false, 0, 100),
                    patients -> list.getItems().setAll(patients));
        };
        debounce.setOnFinished(event -> load.run());
        search.textProperty().addListener((obs, old, value) -> debounce.playFromStart());
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
        return Optional.ofNullable(dialog.showAndWait().orElse(null));
    }

    /** Convenience for flows that only need the id. */
    public static Optional<Long> chooseId(AppContext context, String title) {
        return choose(context, title).map(Patient::id);
    }
}
