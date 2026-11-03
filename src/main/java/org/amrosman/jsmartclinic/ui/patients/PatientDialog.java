package org.amrosman.jsmartclinic.ui.patients;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.Sex;
import org.amrosman.jsmartclinic.service.PatientService;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Forms;
import org.amrosman.jsmartclinic.ui.components.ResponsiveForm;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Add/edit patient form with validation, photo capture and duplicate detection. */
public final class PatientDialog {

    private PatientDialog() {
    }

    public static void open(AppContext context, Patient existing, Consumer<Patient> onSaved) {
        boolean editing = existing != null;

        TextField firstName = Forms.text("First name");
        TextField lastName = Forms.text("Last name");
        DatePicker dob = Forms.date();
        ComboBox<Sex> sex = Forms.combo(List.of(Sex.values()));
        TextField phone = Forms.text("Phone number");
        TextField email = Forms.text("name@example.com");
        TextField address = Forms.text("Street address");
        TextField city = Forms.text("City");
        TextField nationalId = Forms.text("National ID");
        ComboBox<String> bloodType = Forms.combo(List.of("", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"));
        ComboBox<String> maritalStatus = Forms.combo(List.of("", "Single", "Married", "Divorced", "Widowed"));
        TextField occupation = Forms.text("Occupation");
        TextField emergencyName = Forms.text("Contact name");
        TextField emergencyPhone = Forms.text("Contact phone");
        TextField emergencyRelation = Forms.text("Relationship");
        TextField insuranceProvider = Forms.text("Insurance provider");
        TextField insuranceNumber = Forms.text("Policy number");
        TextArea notes = Forms.area("Clinical or administrative notes");

        if (existing != null) {
            firstName.setText(existing.firstName());
            lastName.setText(existing.lastName());
            dob.setValue(existing.dateOfBirth());
            sex.setValue(existing.sex());
            phone.setText(existing.phone());
            email.setText(existing.email());
            address.setText(existing.address());
            city.setText(existing.city());
            nationalId.setText(existing.nationalId());
            bloodType.setValue(existing.bloodType() == null ? "" : existing.bloodType());
            maritalStatus.setValue(existing.maritalStatus() == null ? "" : existing.maritalStatus());
            occupation.setText(existing.occupation());
            emergencyName.setText(existing.emergencyName());
            emergencyPhone.setText(existing.emergencyPhone());
            emergencyRelation.setText(existing.emergencyRelation());
            insuranceProvider.setText(existing.insuranceProvider());
            insuranceNumber.setText(existing.insuranceNumber());
            notes.setText(existing.notes());
        } else {
            sex.setValue(Sex.UNKNOWN);
            bloodType.setValue("");
            maritalStatus.setValue("");
        }

        ImageView photoView = new ImageView();
        photoView.setFitWidth(96);
        photoView.setFitHeight(96);
        photoView.setPreserveRatio(true);
        byte[][] photo = {existing == null ? null : existing.photo()};
        if (photo[0] != null) {
            photoView.setImage(new Image(new ByteArrayInputStream(photo[0])));
        }
        StackPane photoFrame = new StackPane(photoView);
        photoFrame.setMinSize(100, 100);
        photoFrame.setMaxSize(100, 100);
        photoFrame.getStyleClass().add("vital-tile");

        Label photoHint = Forms.errorLabel();
        photoHint.getStyleClass().setAll("muted");
        photoHint.setVisible(true);
        photoHint.setManaged(true);
        photoHint.setText("Optional · resized automatically");

        javafx.scene.control.Button choosePhoto = new javafx.scene.control.Button("Choose photo");
        choosePhoto.getStyleClass().add("secondary-button");
        choosePhoto.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose patient photo");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
            File file = chooser.showOpenDialog(null);
            if (file != null) {
                try {
                    byte[] resized = context.patients().photo(file);
                    photo[0] = resized;
                    photoView.setImage(new Image(new ByteArrayInputStream(resized)));
                } catch (RuntimeException ex) {
                    Forms.showFailure(photoHint, ex);
                }
            }
        });
        javafx.scene.control.Button clearPhoto = new javafx.scene.control.Button("Remove");
        clearPhoto.getStyleClass().add("ghost-button");
        clearPhoto.setOnAction(e -> {
            photo[0] = null;
            photoView.setImage(null);
        });

        Label mrnLabel = new Label(existing == null ? "MRN assigned on save" : existing.mrn());
        mrnLabel.getStyleClass().add("chip");
        Label error = Forms.errorLabel();

        ResponsiveForm form = new ResponsiveForm()
                .section("Identity",
                        ResponsiveForm.field("First name *", firstName),
                        ResponsiveForm.field("Last name *", lastName),
                        ResponsiveForm.field("Date of birth *", dob),
                        ResponsiveForm.field("Sex", sex),
                        ResponsiveForm.field("National ID", nationalId),
                        ResponsiveForm.field("Marital status", maritalStatus),
                        ResponsiveForm.field("Blood type", bloodType),
                        ResponsiveForm.field("Occupation", occupation))
                .section("Contact",
                        ResponsiveForm.field("Phone", phone),
                        ResponsiveForm.field("Email", email),
                        ResponsiveForm.field("Address", address),
                        ResponsiveForm.field("City", city))
                .section("Emergency contact",
                        ResponsiveForm.field("Name", emergencyName),
                        ResponsiveForm.field("Phone", emergencyPhone),
                        ResponsiveForm.field("Relationship", emergencyRelation))
                .section("Insurance",
                        ResponsiveForm.field("Provider", insuranceProvider),
                        ResponsiveForm.field("Policy number", insuranceNumber))
                .section("Notes",
                        ResponsiveForm.wide(ResponsiveForm.field("Notes", notes)));

        photoView.setFitWidth(64);
        photoView.setFitHeight(64);
        photoFrame.setMinSize(68, 68);
        photoFrame.setMaxSize(68, 68);
        HBox photoButtons = new HBox(8, choosePhoto, clearPhoto);
        Label mrnCaption = new Label("Record number");
        mrnCaption.getStyleClass().add("form-label");
        VBox headerInfo = new VBox(6, new HBox(8, mrnCaption, mrnLabel), photoButtons, photoHint);
        HBox header = new HBox(14, photoFrame, headerInfo);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.getStyleClass().add("form-header");

        Dialog<Patient> dialog = Forms.dialog(editing ? "Edit patient" : "Register patient",
                editing ? "Update demographics for " + existing.fullName().trim() : "Create a new patient record",
                new VBox());
        javafx.scene.control.ScrollPane scroll = form.fitInto(dialog, 780, 560);
        VBox content = new VBox(14, header, scroll, error);
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
        content.setPadding(new Insets(4, 4, 0, 4));
        dialog.getDialogPane().setContent(content);
        ButtonType saveType = new ButtonType(editing ? "Save changes" : "Register patient", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Forms.showError(error, null);
            if (firstName.getText().isBlank() || lastName.getText().isBlank()) {
                Forms.showError(error, "First and last name are required");
                return;
            }
            LocalDate birth = dob.getValue();
            if (birth == null) {
                Forms.showError(error, "Date of birth is required");
                return;
            }
            if (birth.isAfter(LocalDate.now())) {
                Forms.showError(error, "Date of birth cannot be in the future");
                return;
            }
            if (!Forms.validPhone(phone.getText())) {
                Forms.showError(error, "Phone number is not valid");
                return;
            }
            if (!Forms.validPhone(emergencyPhone.getText())) {
                Forms.showError(error, "Emergency phone number is not valid");
                return;
            }
            if (!Forms.validEmail(email.getText())) {
                Forms.showError(error, "Email address is not valid");
                return;
            }

            Patient candidate = new Patient(editing ? existing.id() : 0,
                    editing ? existing.mrn() : null,
                    firstName.getText().trim(), lastName.getText().trim(), birth,
                    sex.getValue() == null ? Sex.UNKNOWN : sex.getValue(),
                    blankToNull(phone.getText()), blankToNull(email.getText()), blankToNull(address.getText()),
                    blankToNull(city.getText()), blankToNull(nationalId.getText()), blankToNull(bloodType.getValue()),
                    blankToNull(maritalStatus.getValue()), blankToNull(occupation.getText()),
                    blankToNull(emergencyName.getText()), blankToNull(emergencyPhone.getText()),
                    blankToNull(emergencyRelation.getText()), blankToNull(insuranceProvider.getText()),
                    blankToNull(insuranceNumber.getText()), photo[0], blankToNull(notes.getText()),
                    editing ? existing.status() : "ACTIVE", editing ? existing.deletedAt() : null,
                    editing ? existing.createdAt() : null, editing ? existing.updatedAt() : null);

            dialog.getDialogPane().lookupButton(saveType).setDisable(true);
            Consumer<Patient> persist = patient -> {
                Async.run(() -> editing ? context.patients().update(patient) : context.patients().create(patient, false),
                        saved -> {
                            dialog.setResult(saved);
                            dialog.close();
                        },
                        throwable -> {
                            dialog.getDialogPane().lookupButton(saveType).setDisable(false);
                            if (throwable instanceof PatientService.DuplicatePatientException duplicate) {
                                confirmDuplicate(context, duplicate, patient, dialog, onSaved, error);
                            } else {
                                Forms.showFailure(error, throwable);
                            }
                        });
            };
            if (editing) {
                persist.accept(candidate);
            } else {
                // Run duplicate detection through the service and let the user confirm.
                Async.run(() -> context.patients().duplicates(candidate), duplicates -> {
                    if (!duplicates.isEmpty()) {
                        String names = duplicates.stream().map(d -> d.fullName().trim() + " (" + d.mrn() + ")")
                                .collect(Collectors.joining("\n"));
                        boolean proceed = org.amrosman.jsmartclinic.ui.components.Dialogs.confirm(
                                "Possible duplicate",
                                "A patient with the same name and date of birth already exists:\n\n" + names
                                        + "\n\nRegister another record anyway?");
                        if (!proceed) {
                            dialog.getDialogPane().lookupButton(saveType).setDisable(false);
                            return;
                        }
                    }
                    Async.run(() -> context.patients().create(candidate, true), saved -> {
                        dialog.setResult(saved);
                        dialog.close();
                    }, throwable -> {
                        dialog.getDialogPane().lookupButton(saveType).setDisable(false);
                        Forms.showFailure(error, throwable);
                    });
                }, throwable -> {
                    dialog.getDialogPane().lookupButton(saveType).setDisable(false);
                    Forms.showFailure(error, throwable);
                });
            }
        });

        dialog.setOnHidden(e -> {
            if (dialog.getResult() != null && onSaved != null) {
                onSaved.accept(dialog.getResult());
            }
        });
        dialog.showAndWait();
    }

    private static void confirmDuplicate(AppContext context, PatientService.DuplicatePatientException duplicate,
                                         Patient patient, Dialog<Patient> dialog, Consumer<Patient> onSaved,
                                         Label error) {
        String names = duplicate.duplicates().stream().map(d -> d.fullName().trim() + " (" + d.mrn() + ")")
                .collect(Collectors.joining("\n"));
        if (!org.amrosman.jsmartclinic.ui.components.Dialogs.confirm("Possible duplicate",
                "The patient was not saved because a matching record exists:\n\n" + names
                        + "\n\nSave anyway?")) {
            return;
        }
        Async.run(() -> context.patients().create(patient, true), saved -> {
            dialog.setResult(saved);
            dialog.close();
        }, throwable -> Forms.showFailure(error, throwable));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static Optional<Patient> edit(AppContext context, Patient patient) {
        open(context, patient, null);
        return Optional.ofNullable(patient);
    }
}
