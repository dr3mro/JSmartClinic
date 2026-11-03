package org.amrosman.jsmartclinic.ui.settings;

import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.app.ContextAware;
import org.amrosman.jsmartclinic.app.Navigator;
import org.amrosman.jsmartclinic.domain.AuditEvent;
import org.amrosman.jsmartclinic.domain.BillableService;
import org.amrosman.jsmartclinic.domain.LabTest;
import org.amrosman.jsmartclinic.domain.Role;
import org.amrosman.jsmartclinic.domain.User;
import org.amrosman.jsmartclinic.service.Permission;
import org.amrosman.jsmartclinic.service.SettingsService.ClinicProfile;
import org.amrosman.jsmartclinic.ui.ThemeManager;
import org.amrosman.jsmartclinic.ui.components.Async;
import org.amrosman.jsmartclinic.ui.components.Dialogs;
import org.amrosman.jsmartclinic.ui.components.Forms;
import org.amrosman.jsmartclinic.ui.components.Toasts;
import org.amrosman.jsmartclinic.ui.components.Ui;
import org.amrosman.jsmartclinic.util.Dates;
import org.amrosman.jsmartclinic.util.I18n;
import org.amrosman.jsmartclinic.util.Money;
import org.amrosman.jsmartclinic.util.PasswordHasher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Clinic profile, users, catalogues, backups and the audit trail. */
public class SettingsController implements ContextAware, Navigator.Refreshable {

    @FXML private TabPane settingsTabs;
    @FXML private TextField profileName;
    @FXML private TextField profileAddress;
    @FXML private TextField profilePhone;
    @FXML private TextField profileEmail;
    @FXML private TextField profileLogo;
    @FXML private TextField profileCurrency;
    @FXML private TextField profileTax;
    @FXML private ComboBox<String> themeBox;
    @FXML private ComboBox<String> languageBox;
    @FXML private CheckBox autoBackup;
    @FXML private Label profileStatus;

    @FXML private Label userCountLabel;
    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, String> userNameColumn;
    @FXML private TableColumn<User, String> userFullNameColumn;
    @FXML private TableColumn<User, String> userRoleColumn;
    @FXML private TableColumn<User, String> userSpecialtyColumn;
    @FXML private TableColumn<User, String> userActiveColumn;
    @FXML private TableColumn<User, String> userLastLoginColumn;
    @FXML private Button resetPasswordButton;
    @FXML private Button editUserButton;
    @FXML private Button newUserButton;

    @FXML private Label labCountLabel;
    @FXML private TableView<LabTest> labTable;
    @FXML private TableColumn<LabTest, String> labCodeColumn;
    @FXML private TableColumn<LabTest, String> labNameColumn;
    @FXML private TableColumn<LabTest, String> labUnitColumn;
    @FXML private TableColumn<LabTest, String> labRangeColumn;
    @FXML private TableColumn<LabTest, String> labCategoryColumn;
    @FXML private Button deleteLabButton;
    @FXML private Button editLabButton;
    @FXML private Label serviceCountLabel;
    @FXML private TableView<BillableService> serviceTable;
    @FXML private TableColumn<BillableService, String> serviceCodeColumn;
    @FXML private TableColumn<BillableService, String> serviceNameColumn;
    @FXML private TableColumn<BillableService, String> servicePriceColumn;
    @FXML private TableColumn<BillableService, String> serviceActiveColumn;
    @FXML private Button editServiceButton;

    @FXML private Label backupStatus;
    @FXML private ListView<Path> backupList;
    @FXML private Button restoreButton;

    @FXML private Label auditCountLabel;
    @FXML private TableView<AuditRow> auditTable;
    @FXML private TableColumn<AuditRow, String> auditAtColumn;
    @FXML private TableColumn<AuditRow, String> auditUserColumn;
    @FXML private TableColumn<AuditRow, String> auditActionColumn;
    @FXML private TableColumn<AuditRow, String> auditEntityColumn;
    @FXML private TableColumn<AuditRow, String> auditDetailsColumn;

    private AppContext context;
    private String loadedLanguage = "en";

    @Override
    public void init(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        if (context == null) {
            context = AppContext.get();
        }
        boolean allowed = context.session().can(Permission.MANAGE_USERS_SETTINGS);
        themeBox.getItems().setAll("Light", "Dark");
        languageBox.getItems().setAll("English", "Arabic (العربية)");

        configureUsers();
        configureCatalogues();
        configureAudit();

        editUserButton.disableProperty().bind(userTable.getSelectionModel().selectedItemProperty().isNull());
        resetPasswordButton.disableProperty().bind(userTable.getSelectionModel().selectedItemProperty().isNull());
        editLabButton.disableProperty().bind(labTable.getSelectionModel().selectedItemProperty().isNull());
        deleteLabButton.disableProperty().bind(labTable.getSelectionModel().selectedItemProperty().isNull());
        editServiceButton.disableProperty().bind(serviceTable.getSelectionModel().selectedItemProperty().isNull());
        restoreButton.disableProperty().bind(backupList.getSelectionModel().selectedItemProperty().isNull());

        if (!allowed) {
            profileStatus.setText("Your role cannot change settings");
        }
        loadProfile();
        loadUsers();
        loadCatalogue();
        loadBackups();
        loadAudit();
    }

    @Override
    public void refresh() {
        loadProfile();
        loadUsers();
        loadCatalogue();
        loadBackups();
        loadAudit();
    }

    private void configureUsers() {
        userTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        userNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().username()));
        userFullNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().fullName()));
        userRoleColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().role().label()));
        userSpecialtyColumn.setCellValueFactory(data -> new SimpleStringProperty(blank(data.getValue().specialty())));
        userActiveColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().active() ? "Yes" : "No"));
        userLastLoginColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().lastLoginAt() == null ? "Never" : Dates.display(data.getValue().lastLoginAt())));
        userTable.setPlaceholder(Ui.emptyState("No users", "Create the first staff account"));
        userTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                onEditUser();
            }
        });
    }

    private void configureCatalogues() {
        labTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        labCodeColumn.setCellValueFactory(data -> new SimpleStringProperty(blank(data.getValue().code())));
        labNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        labUnitColumn.setCellValueFactory(data -> new SimpleStringProperty(blank(data.getValue().unit())));
        labRangeColumn.setCellValueFactory(data -> new SimpleStringProperty(range(data.getValue())));
        labCategoryColumn.setCellValueFactory(data -> new SimpleStringProperty(blank(data.getValue().category())));
        labTable.setPlaceholder(Ui.emptyState("No lab tests", "Add the tests your practice offers"));
        labTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                onEditLab();
            }
        });

        serviceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        serviceCodeColumn.setCellValueFactory(data -> new SimpleStringProperty(blank(data.getValue().code())));
        serviceNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        servicePriceColumn.setCellValueFactory(data -> new SimpleStringProperty(
                Money.format(data.getValue().priceCents(), context.settings().profile().currency())));
        serviceActiveColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().active() ? "Active" : "Inactive"));
        serviceActiveColumn.setCellFactory(Ui.badgeCell());
        serviceTable.setPlaceholder(Ui.emptyState("No services", "Add billable services to create invoices"));
        serviceTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                onEditService();
            }
        });
    }

    private void configureAudit() {
        auditTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        auditAtColumn.setCellValueFactory(data -> new SimpleStringProperty(Dates.display(data.getValue().event().at())));
        auditUserColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().userName()));
        auditActionColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().event().action().name()));
        auditActionColumn.setCellFactory(Ui.badgeCell());
        auditEntityColumn.setCellValueFactory(data -> new SimpleStringProperty(entity(data.getValue().event())));
        auditDetailsColumn.setCellValueFactory(data -> new SimpleStringProperty(
                blank(data.getValue().event().details())));
        auditTable.setPlaceholder(Ui.emptyState("No audit events", "Data changes and views appear here"));
    }

    // ---------- Clinic profile ----------

    private void loadProfile() {
        Async.run(() -> context.settings().profile(), this::fillProfile, Dialogs::error);
    }

    private void fillProfile(ClinicProfile profile) {
        profileName.setText(profile.name());
        profileAddress.setText(profile.address());
        profilePhone.setText(profile.phone());
        profileEmail.setText(profile.email());
        profileLogo.setText(profile.logoPath());
        profileCurrency.setText(profile.currency());
        profileTax.setText(String.valueOf(profile.taxRate()));
        themeBox.setValue("dark".equalsIgnoreCase(profile.theme()) ? "Dark" : "Light");
        languageBox.setValue("ar".equalsIgnoreCase(profile.language()) ? "Arabic (العربية)" : "English");
        autoBackup.setSelected(profile.autoBackup());
        loadedLanguage = profile.language();
        profileStatus.setText("Loaded");
    }

    @FXML
    private void onBrowseLogo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose clinic logo");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(settingsTabs.getScene() == null
                ? null : settingsTabs.getScene().getWindow());
        if (file != null) {
            profileLogo.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void onSaveProfile() {
        String name = profileName.getText() == null ? "" : profileName.getText().trim();
        if (name.isBlank()) {
            profileStatus.setText("Clinic name is required");
            return;
        }
        if (!Forms.validPhone(profilePhone.getText())) {
            profileStatus.setText("Enter a valid phone number");
            return;
        }
        if (!Forms.validEmail(profileEmail.getText())) {
            profileStatus.setText("Enter a valid email address");
            return;
        }
        int tax;
        try {
            tax = Math.max(0, Math.min(100, Integer.parseInt(profileTax.getText().trim())));
        } catch (NumberFormatException e) {
            profileStatus.setText("Tax rate must be a whole number between 0 and 100");
            return;
        }
        String theme = "Dark".equals(themeBox.getValue()) ? "dark" : "light";
        String language = "Arabic (العربية)".equals(languageBox.getValue()) ? "ar" : "en";
        ClinicProfile profile = new ClinicProfile(
                name,
                text(profileAddress),
                text(profilePhone),
                text(profileEmail),
                text(profileLogo),
                text(profileCurrency).isBlank() ? "$" : text(profileCurrency),
                tax, theme, language, autoBackup.isSelected());
        profileStatus.setText("Saving…");
        Async.run(() -> {
            context.settings().saveProfile(profile);
            context.settings().setTheme(theme);
            context.settings().setLanguage(language);
            return null;
        }, ignored -> {
            applyPreferences(theme, language);
            profileStatus.setText("Saved " + Dates.display(Instant.now()));
            Toasts.success("Settings saved");
            if (!language.equals(loadedLanguage)) {
                loadedLanguage = language;
                Dialogs.info("Language saved. Sign out and back in to update all navigation labels.");
            }
        }, throwable -> {
            profileStatus.setText("Unable to save settings");
            Dialogs.error(throwable);
        });
    }

    private void applyPreferences(String theme, String language) {
        I18n.setLanguage(language);
        Scene scene = settingsTabs.getScene();
        if (scene != null) {
            ThemeManager.apply(scene, theme, "ar".equalsIgnoreCase(language));
        }
    }

    // ---------- Users ----------

    private void loadUsers() {
        userCountLabel.setText("Loading…");
        Async.run(() -> context.users().all(), users -> {
            userTable.getItems().setAll(users);
            userCountLabel.setText(users.size() + " user" + (users.size() == 1 ? "" : "s"));
        }, throwable -> {
            userCountLabel.setText("Unable to load users");
            Dialogs.error(throwable);
        });
    }

    @FXML
    private void onRefreshUsers() {
        loadUsers();
    }

    @FXML
    private void onNewUser() {
        openUserDialog(null);
    }

    @FXML
    private void onEditUser() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openUserDialog(selected);
        }
    }

    private void openUserDialog(User existing) {
        boolean creating = existing == null;
        TextField username = Forms.text("Username");
        TextField fullName = Forms.text("Full name");
        ComboBox<Role> role = roleCombo();
        TextField specialty = Forms.text("Specialty (optional)");
        CheckBox clinician = new CheckBox("I also see patients (doctor)");
        CheckBox active = new CheckBox("Active account");
        active.setSelected(true);
        PasswordField password = new PasswordField();
        password.setPromptText("At least " + PasswordHasher.MIN_LENGTH + " characters");
        password.getStyleClass().add("form-field");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Repeat password");
        confirm.getStyleClass().add("form-field");

        if (!creating) {
            username.setText(existing.username());
            username.setDisable(true);
            fullName.setText(existing.fullName());
            role.setValue(existing.role());
            specialty.setText(existing.specialty());
            clinician.setSelected(existing.clinician());
            active.setSelected(existing.active());
        } else {
            role.setValue(Role.RECEPTION);
        }

        GridPane grid = Forms.grid();
        int row = 0;
        Forms.row(grid, row++, "Username", username);
        Forms.row(grid, row++, "Full name", fullName);
        Forms.row(grid, row++, "Role", role);
        Forms.row(grid, row++, "Specialty", specialty);
        Forms.row(grid, row++, "Clinical access", clinician);
        if (creating) {
            Forms.row(grid, row++, "Password", password);
            Forms.row(grid, row++, "Confirm", confirm);
        } else {
            Forms.row(grid, row++, "Status", active);
        }
        Label error = Forms.errorLabel();
        VBox content = Forms.stack(grid, error);
        content.setPrefWidth(460);

        Dialog<Void> dialog = Forms.dialog(creating ? "New user" : "Edit user",
                creating ? "Create a staff account" : existing.fullName(), content);
        ButtonType saveType = new ButtonType(creating ? "Create user" : "Save user", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Forms.showError(error, null);
            String userName = text(username);
            String name = text(fullName);
            if (userName.isBlank()) {
                Forms.showError(error, "Username is required");
                return;
            }
            if (name.isBlank()) {
                Forms.showError(error, "Full name is required");
                return;
            }
            if (role.getValue() == null) {
                Forms.showError(error, "Choose a role");
                return;
            }
            if (creating) {
                if (!password.getText().equals(confirm.getText())) {
                    Forms.showError(error, "Passwords do not match");
                    return;
                }
                try {
                    PasswordHasher.validatePolicy(password.getText());
                } catch (RuntimeException e) {
                    Forms.showError(error, e.getMessage());
                    return;
                }
            }
            dialog.getDialogPane().lookupButton(saveType).setDisable(true);
            Async.run(() -> {
                if (creating) {
                    context.users().create(userName, name, role.getValue(), text(specialty), password.getText(),
                            clinician.isSelected());
                } else {
                    context.users().update(existing.id(), name, role.getValue(), text(specialty), active.isSelected(),
                            clinician.isSelected());
                }
                return null;
            }, ignored -> {
                dialog.close();
                Toasts.success(creating ? "User created" : "User updated");
                loadUsers();
            }, throwable -> {
                dialog.getDialogPane().lookupButton(saveType).setDisable(false);
                Forms.showFailure(error, throwable);
            });
        });
        dialog.showAndWait();
    }

    @FXML
    private void onResetPassword() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        PasswordField password = new PasswordField();
        password.setPromptText("At least " + PasswordHasher.MIN_LENGTH + " characters");
        password.getStyleClass().add("form-field");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Repeat password");
        confirm.getStyleClass().add("form-field");

        GridPane grid = Forms.grid();
        Forms.row(grid, 0, "New password", password);
        Forms.row(grid, 1, "Confirm", confirm);
        Label error = Forms.errorLabel();
        Dialog<Void> dialog = Forms.dialog("Reset password", selected.username(), Forms.stack(grid, error));
        ButtonType saveType = new ButtonType("Reset password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Forms.showError(error, null);
            if (!password.getText().equals(confirm.getText())) {
                Forms.showError(error, "Passwords do not match");
                return;
            }
            try {
                PasswordHasher.validatePolicy(password.getText());
            } catch (RuntimeException e) {
                Forms.showError(error, e.getMessage());
                return;
            }
            dialog.getDialogPane().lookupButton(saveType).setDisable(true);
            Async.run(() -> {
                context.users().resetPassword(selected.id(), password.getText());
                return null;
            }, ignored -> {
                dialog.close();
                Toasts.success("Password reset");
            }, throwable -> {
                dialog.getDialogPane().lookupButton(saveType).setDisable(false);
                Forms.showFailure(error, throwable);
            });
        });
        dialog.showAndWait();
    }

    private static ComboBox<Role> roleCombo() {
        ComboBox<Role> combo = Forms.combo(List.of(Role.values()));
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Role role) {
                return role == null ? "" : role.label();
            }

            @Override
            public Role fromString(String value) {
                return null;
            }
        });
        return combo;
    }

    // ---------- Catalogues ----------

    private void loadCatalogue() {
        labCountLabel.setText("Loading…");
        serviceCountLabel.setText("Loading…");
        Async.run(() -> {
            List<LabTest> tests = context.labs().tests();
            List<BillableService> services = context.billing().services(false);
            return new Catalogue(tests, services);
        }, catalogue -> {
            labTable.getItems().setAll(catalogue.tests());
            labCountLabel.setText(catalogue.tests().size() + " test"
                    + (catalogue.tests().size() == 1 ? "" : "s"));
            serviceTable.getItems().setAll(catalogue.services());
            serviceCountLabel.setText(catalogue.services().size() + " service"
                    + (catalogue.services().size() == 1 ? "" : "s"));
        }, throwable -> {
            labCountLabel.setText("Unable to load catalogue");
            serviceCountLabel.setText("Unable to load catalogue");
            Dialogs.error(throwable);
        });
    }

    @FXML
    private void onNewLab() {
        openLabDialog(null);
    }

    @FXML
    private void onEditLab() {
        LabTest selected = labTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openLabDialog(selected);
        }
    }

    private void openLabDialog(LabTest existing) {
        boolean creating = existing == null;
        TextField code = Forms.text("e.g. HGB");
        TextField name = Forms.text("Test name");
        TextField unit = Forms.text("e.g. g/dL");
        TextField low = Forms.text("Low");
        TextField high = Forms.text("High");
        TextField category = Forms.text("e.g. Haematology");
        if (!creating) {
            code.setText(existing.code());
            name.setText(existing.name());
            unit.setText(existing.unit());
            low.setText(existing.refLow() == null ? "" : String.valueOf(existing.refLow()));
            high.setText(existing.refHigh() == null ? "" : String.valueOf(existing.refHigh()));
            category.setText(existing.category());
        }

        GridPane grid = Forms.grid();
        Forms.row(grid, 0, "Code", code);
        Forms.row(grid, 1, "Test name", name);
        Forms.row(grid, 2, "Unit", unit);
        Forms.row(grid, 3, "Reference low", low);
        Forms.row(grid, 4, "Reference high", high);
        Forms.row(grid, 5, "Category", category);
        Label error = Forms.errorLabel();
        Dialog<Void> dialog = Forms.dialog(creating ? "New lab test" : "Edit lab test",
                "Reference ranges drive automatic LOW/HIGH/CRITICAL flags", Forms.stack(grid, error));
        ButtonType saveType = new ButtonType(creating ? "Create test" : "Save test", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Forms.showError(error, null);
            if (text(name).isBlank()) {
                Forms.showError(error, "Test name is required");
                return;
            }
            Double lowValue;
            Double highValue;
            try {
                lowValue = parseDouble(low.getText());
                highValue = parseDouble(high.getText());
            } catch (NumberFormatException e) {
                Forms.showError(error, "Reference values must be numbers");
                return;
            }
            if (lowValue != null && highValue != null && lowValue > highValue) {
                Forms.showError(error, "Reference low must be below reference high");
                return;
            }
            dialog.getDialogPane().lookupButton(saveType).setDisable(true);
            Async.run(() -> {
                context.labs().saveTest(creating ? 0 : existing.id(), text(code), text(name), text(unit),
                        lowValue, highValue, text(category));
                return null;
            }, ignored -> {
                dialog.close();
                Toasts.success(creating ? "Lab test created" : "Lab test saved");
                loadCatalogue();
            }, throwable -> {
                dialog.getDialogPane().lookupButton(saveType).setDisable(false);
                Forms.showFailure(error, throwable);
            });
        });
        dialog.showAndWait();
    }

    @FXML
    private void onDeleteLab() {
        LabTest selected = labTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (!Dialogs.confirm("Delete lab test", "Delete " + selected.name()
                + "? Existing results keep their recorded values.")) {
            return;
        }
        long id = selected.id();
        Async.run(() -> {
            context.labs().deleteTest(id);
            return null;
        }, ignored -> {
            Toasts.success("Lab test deleted");
            loadCatalogue();
        });
    }

    @FXML
    private void onNewService() {
        openServiceDialog(null);
    }

    @FXML
    private void onEditService() {
        BillableService selected = serviceTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openServiceDialog(selected);
        }
    }

    private void openServiceDialog(BillableService existing) {
        boolean creating = existing == null;
        TextField code = Forms.text("e.g. CONS");
        TextField name = Forms.text("Service name");
        TextField price = Forms.text("0.00");
        CheckBox active = new CheckBox("Available for new invoices");
        active.setSelected(true);
        if (!creating) {
            code.setText(existing.code());
            name.setText(existing.name());
            price.setText(String.format(Locale.US, "%.2f", existing.priceCents() / 100.0));
            active.setSelected(existing.active());
        }

        GridPane grid = Forms.grid();
        Forms.row(grid, 0, "Code", code);
        Forms.row(grid, 1, "Service", name);
        Forms.row(grid, 2, "Price", price);
        Forms.row(grid, 3, "Status", active);
        Label error = Forms.errorLabel();
        Dialog<Void> dialog = Forms.dialog(creating ? "New service" : "Edit service",
                "The tax rate from the clinic profile is applied at invoicing", Forms.stack(grid, error));
        ButtonType saveType = new ButtonType(creating ? "Create service" : "Save service",
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Forms.showError(error, null);
            if (text(name).isBlank()) {
                Forms.showError(error, "Service name is required");
                return;
            }
            long cents;
            try {
                cents = Money.parseToCents(price.getText());
            } catch (RuntimeException e) {
                Forms.showError(error, "Enter a valid price");
                return;
            }
            if (cents < 0) {
                Forms.showError(error, "Price cannot be negative");
                return;
            }
            dialog.getDialogPane().lookupButton(saveType).setDisable(true);
            Async.run(() -> {
                context.billing().saveService(creating ? 0 : existing.id(), text(code), text(name), cents,
                        active.isSelected());
                return null;
            }, ignored -> {
                dialog.close();
                Toasts.success(creating ? "Service created" : "Service saved");
                loadCatalogue();
            }, throwable -> {
                dialog.getDialogPane().lookupButton(saveType).setDisable(false);
                Forms.showFailure(error, throwable);
            });
        });
        dialog.showAndWait();
    }

    // ---------- Backups ----------

    private void loadBackups() {
        backupStatus.setText("Loading…");
        Async.run(() -> context.backups().backups(), backups -> {
            backupList.getItems().setAll(backups);
            backupStatus.setText(backups.size() + " backup" + (backups.size() == 1 ? "" : "s"));
            if (backups.isEmpty()) {
                backupList.setPlaceholder(Ui.emptyState("No backups yet", "Create one before making major changes"));
            }
            backupList.setCellFactory(view -> new ListCell<>() {
                @Override
                protected void updateItem(Path item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item.getFileName() + "   ·   " + size(item));
                    }
                }
            });
        }, throwable -> {
            backupStatus.setText("Unable to load backups");
            Dialogs.error(throwable);
        });
    }

    @FXML
    private void onRefreshBackups() {
        loadBackups();
    }

    @FXML
    private void onBackupNow() {
        backupStatus.setText("Creating backup…");
        Async.run(() -> context.backups().backupNow(), path -> {
            Toasts.success("Backup created");
            backupStatus.setText("Saved " + path.getFileName());
            loadBackups();
        }, throwable -> {
            backupStatus.setText("Backup failed");
            Dialogs.error(throwable);
        });
    }

    @FXML
    private void onRestore() {
        Path selected = backupList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (!Dialogs.confirm("Restore backup", "Replace the current database with "
                + selected.getFileName() + "?\nThis cannot be undone.")) {
            return;
        }
        backupStatus.setText("Restoring…");
        Async.run(() -> {
            context.backups().restore(selected);
            return null;
        }, ignored -> {
            backupStatus.setText("Restore complete");
            Dialogs.info("Backup restored. Restart JSmartClinic before continuing.");
        }, throwable -> {
            backupStatus.setText("Restore failed");
            Dialogs.error(throwable);
        });
    }

    // ---------- Audit ----------

    private void loadAudit() {
        auditCountLabel.setText("Loading…");
        Async.run(() -> {
            List<AuditEvent> events = context.audit().recent(400);
            Map<Long, String> names = new HashMap<>();
            List<AuditRow> rows = new ArrayList<>(events.size());
            for (AuditEvent event : events) {
                String name;
                if (event.userId() == null) {
                    name = "System";
                } else {
                    name = names.computeIfAbsent(event.userId(), id -> {
                        User user = context.users().byId(id);
                        return user == null ? "#" + id : user.fullName();
                    });
                }
                rows.add(new AuditRow(event, name));
            }
            return rows;
        }, rows -> {
            auditTable.getItems().setAll(rows);
            auditCountLabel.setText(rows.size() + " event" + (rows.size() == 1 ? "" : "s"));
        }, throwable -> {
            auditCountLabel.setText("Unable to load audit log");
            Dialogs.error(throwable);
        });
    }

    @FXML
    private void onRefreshAudit() {
        loadAudit();
    }

    // ---------- Helpers ----------

    private static String text(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String range(LabTest test) {
        if (test.refLow() == null && test.refHigh() == null) {
            return "—";
        }
        if (test.refLow() != null && test.refHigh() != null) {
            return test.refLow() + " – " + test.refHigh();
        }
        return test.refLow() != null ? "≥ " + test.refLow() : "≤ " + test.refHigh();
    }

    private static String entity(AuditEvent event) {
        String entity = event.entity() == null ? "—" : event.entity();
        return event.entityId() == null ? entity : entity + " #" + event.entityId();
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Double.valueOf(value.trim());
    }

    private static String size(Path path) {
        try {
            long bytes = Files.size(path);
            if (bytes < 1024) {
                return bytes + " B";
            }
            if (bytes < 1024 * 1024) {
                return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
            }
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        } catch (IOException e) {
            return "—";
        }
    }

    private record Catalogue(List<LabTest> tests, List<BillableService> services) {
    }

    private record AuditRow(AuditEvent event, String userName) {
    }
}
