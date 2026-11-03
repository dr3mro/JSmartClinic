package org.amrosman.jsmartclinic.ui.appointments;

import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.Appointment;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.User;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Dialogs;
import org.amrosman.jsmartclinic.ui.components.Forms;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Create or reschedule an appointment. */
public final class AppointmentDialog {

    private static final DateTimeFormatter TIME_INPUT = DateTimeFormatter.ofPattern("H:mm");

    private AppointmentDialog() {
    }

    public static void open(AppContext context, Appointment existing, Patient presetPatient, Consumer<Long> onSaved) {
        Async.run(() -> {
            List<User> providers = context.users().providers();
            List<Patient> patients = new ArrayList<>(context.patients().search("", false, 0, 500));
            if (existing != null && patients.stream().noneMatch(p -> p.id() == existing.patientId())) {
                Patient current = context.patients().basic(existing.patientId());
                if (current != null) {
                    patients.add(0, current);
                }
            }
            if (presetPatient != null && patients.stream().noneMatch(p -> p.id() == presetPatient.id())) {
                patients.add(0, presetPatient);
            }
            return new Data(providers, patients);
        }, data -> build(context, existing, presetPatient, data, onSaved), Dialogs::error);
    }

    private static void build(AppContext context, Appointment existing, Patient presetPatient, Data data,
                              Consumer<Long> onSaved) {
        ComboBox<Patient> patient = new ComboBox<>();
        patient.setEditable(true);
        patient.getStyleClass().add("form-field");
        patient.setMaxWidth(Double.MAX_VALUE);
        patient.getItems().setAll(data.patients());
        patient.setPromptText("Search by name or MRN");
        patient.setConverter(new StringConverter<>() {
            @Override
            public String toString(Patient value) {
                return value == null ? "" : value.fullName().trim() + " · " + value.mrn();
            }

            @Override
            public Patient fromString(String string) {
                return null;
            }
        });
        patient.getEditor().textProperty().addListener((obs, old, value) -> {
            if (patient.getSelectionModel().getSelectedItem() != null
                    && patient.getConverter().toString(patient.getSelectionModel().getSelectedItem()).equals(value)) {
                return;
            }
            String query = value == null ? "" : value.toLowerCase();
            List<Patient> matches = data.patients().stream()
                    .filter(p -> p.fullName().toLowerCase().contains(query)
                            || p.mrn().toLowerCase().contains(query))
                    .limit(25)
                    .toList();
            patient.getItems().setAll(matches);
            if (!matches.isEmpty() && value != null && !value.isBlank()) {
                patient.show();
            }
        });

        ComboBox<User> provider = Forms.combo(data.providers());
        provider.setConverter(new StringConverter<>() {
            @Override
            public String toString(User value) {
                return value == null ? "" : value.fullName();
            }

            @Override
            public User fromString(String string) {
                return null;
            }
        });

        DatePicker date = Forms.date();
        TextField time = Forms.text("HH:mm");
        Spinner<Integer> duration = new Spinner<>(5, 240, 30, 5);
        duration.setEditable(true);
        duration.getStyleClass().add("form-field");
        TextField reason = Forms.text("Reason for the visit");
        TextArea notes = Forms.area("Internal notes");
        Label error = Forms.errorLabel();

        Patient selectedPatient = existing != null
                ? data.patients().stream().filter(p -> p.id() == existing.patientId()).findFirst().orElse(presetPatient)
                : presetPatient;
        patient.getSelectionModel().select(selectedPatient);
        if (selectedPatient != null) {
            patient.getEditor().setText(patient.getConverter().toString(selectedPatient));
        }
        if (existing != null) {
            LocalDate existingDate = existing.startAt().atZone(ZoneId.systemDefault()).toLocalDate();
            date.setValue(existingDate);
            time.setText(existing.startAt().atZone(ZoneId.systemDefault()).toLocalTime().format(TIME_INPUT));
            duration.getValueFactory().setValue((int) Math.max(5, existing.durationMinutes()));
            reason.setText(existing.reason() == null ? "" : existing.reason());
            notes.setText(existing.notes() == null ? "" : existing.notes());
            provider.getSelectionModel().select(data.providers().stream()
                    .filter(u -> u.id() == existing.providerId()).findFirst().orElse(null));
        } else {
            date.setValue(LocalDate.now());
            LocalTime next = LocalTime.now().plusMinutes(30 - LocalTime.now().getMinute() % 30)
                    .withSecond(0).withNano(0);
            time.setText(next.format(TIME_INPUT));
            User current = context.session().current();
            if (current != null) {
                provider.getSelectionModel().select(data.providers().stream()
                        .filter(u -> u.id() == current.id()).findFirst().orElse(null));
            }
            if (provider.getSelectionModel().getSelectedItem() == null && !data.providers().isEmpty()) {
                provider.getSelectionModel().selectFirst();
            }
        }

        GridPane grid = Forms.grid();
        Forms.row(grid, 0, "Patient *", patient);
        Forms.row(grid, 1, "Provider *", provider);
        Forms.row(grid, 2, "Date *", date);
        Forms.row(grid, 3, "Start time *", time);
        Forms.row(grid, 4, "Duration (min)", duration);
        Forms.row(grid, 5, "Reason", reason);
        Forms.row(grid, 6, "Notes", notes);

        Dialog<Void> dialog = Forms.dialog(existing == null ? "New appointment" : "Reschedule appointment",
                existing == null ? "Book a visit and assign a provider" : "Update the appointment details",
                new VBox(10, grid, error));
        ButtonType saveType = new ButtonType(existing == null ? "Book appointment" : "Save changes",
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Forms.showError(error, null);
            Patient chosen = patient.getSelectionModel().getSelectedItem();
            if (chosen == null) {
                Forms.showError(error, "Choose a patient");
                return;
            }
            User chosenProvider = provider.getSelectionModel().getSelectedItem();
            if (chosenProvider == null) {
                Forms.showError(error, "Choose a provider");
                return;
            }
            if (date.getValue() == null) {
                Forms.showError(error, "Choose a date");
                return;
            }
            LocalTime parsed;
            try {
                parsed = LocalTime.parse(time.getText() == null ? "" : time.getText().trim(), TIME_INPUT);
            } catch (DateTimeParseException e) {
                Forms.showError(error, "Enter the start time as HH:mm");
                return;
            }
            int minutes = duration.getValue() == null ? 30 : duration.getValue();
            var start = date.getValue().atTime(parsed).atZone(ZoneId.systemDefault()).toInstant();
            var end = start.plus(minutes, ChronoUnit.MINUTES);
            Patient finalPatient = chosen;
            dialog.getDialogPane().lookupButton(saveType).setDisable(true);
            Async.run(() -> {
                if (existing == null) {
                    return context.scheduling().create(finalPatient.id(), chosenProvider.id(), start, end,
                            blankToNull(reason.getText()), blankToNull(notes.getText()));
                }
                context.scheduling().update(existing.id(), finalPatient.id(), chosenProvider.id(), start, end,
                        blankToNull(reason.getText()), blankToNull(notes.getText()));
                return existing.id();
            }, id -> {
                dialog.setResult(null);
                dialog.close();
                if (onSaved != null) {
                    onSaved.accept(id);
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

    private record Data(List<User> providers, List<Patient> patients) {
    }
}
