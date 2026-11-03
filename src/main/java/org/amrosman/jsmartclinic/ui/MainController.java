package org.amrosman.jsmartclinic.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.util.Duration;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.app.ContextAware;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.User;
import org.amrosman.jsmartclinic.service.Permission;
import org.amrosman.jsmartclinic.ui.components.Dialogs;
import org.amrosman.jsmartclinic.ui.components.Toasts;
import org.amrosman.jsmartclinic.ui.patients.PatientDialog;
import org.amrosman.jsmartclinic.util.Dates;
import org.amrosman.jsmartclinic.util.I18n;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class MainController implements ContextAware {

    @FXML private ToggleGroup navGroup;
    @FXML private ToggleButton navDashboard;
    @FXML private ToggleButton navPatients;
    @FXML private ToggleButton navAppointments;
    @FXML private ToggleButton navEncounters;
    @FXML private ToggleButton navPrescriptions;
    @FXML private ToggleButton navLabs;
    @FXML private ToggleButton navBilling;
    @FXML private ToggleButton navReports;
    @FXML private ToggleButton navSettings;
    @FXML private Label mainSectionLabel;
    @FXML private Label clinicalSectionLabel;
    @FXML private Label adminSectionLabel;
    @FXML private Label sidebarAvatar;
    @FXML private Label sidebarName;
    @FXML private Label sidebarRole;
    @FXML private Button logoutButton;
    @FXML private Label pageTitle;
    @FXML private Label dateLabel;
    @FXML private TextField searchField;
    @FXML private Button newPatientButton;
    @FXML private StackPane contentArea;

    private AppContext context;
    private Scene scene;
    private Runnable onLogout = () -> {
    };
    private Runnable onLockExpired = () -> {
    };
    private final PauseTransition inactivity = new PauseTransition(Duration.minutes(10));
    private ContextMenu searchResults;
    private boolean lockShowing;

    @Override
    public void init(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        if (context == null) {
            context = AppContext.get();
        }
        User user = context.session().current();
        if (user != null) {
            sidebarName.setText(user.fullName());
            sidebarRole.setText(user.role().label() + (user.specialty() == null ? "" : " · " + user.specialty()));
            sidebarAvatar.setText(initials(user.fullName()));
        }
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)));
        applyPermissions();
        applyLanguage();
        context.navigator().attach(contentArea);
        context.navigator().setOnNavigate(this::onNavigated);
        context.navigator().show("dashboard");
        setupSearch();
        inactivity.setOnFinished(e -> showLockDialog());
    }

    public void installShortcuts(Scene scene) {
        this.scene = scene;
        ThemeManager.apply(scene, context.settings().profile().theme(), I18n.rightToLeft());
        scene.addEventFilter(MouseEvent.ANY, e -> context.session().touch());
        scene.addEventFilter(KeyEvent.ANY, e -> {
            context.session().touch();
            resetInactivity();
        });
        scene.getAccelerators().put(KeyCombination.keyCombination("Shortcut+N"), this::onNewPatient);
        scene.getAccelerators().put(KeyCombination.keyCombination("Shortcut+F"), () -> {
            searchField.requestFocus();
            searchField.selectAll();
        });
        resetInactivity();
    }

    public void setOnLogout(Runnable onLogout) {
        this.onLogout = onLogout;
    }

    public void setOnLockExpired(Runnable onLockExpired) {
        this.onLockExpired = onLockExpired;
    }

    private void applyPermissions() {
        boolean canViewClinical = context.session().can(Permission.VIEW_CLINICAL);
        navAppointments.setVisible(context.session().can(Permission.SCHEDULE));
        navAppointments.setManaged(navAppointments.isVisible());
        navEncounters.setVisible(canViewClinical);
        navEncounters.setManaged(canViewClinical);
        navPrescriptions.setVisible(canViewClinical);
        navPrescriptions.setManaged(canViewClinical);
        navLabs.setVisible(context.session().can(Permission.LABS));
        navLabs.setManaged(navLabs.isVisible());
        navBilling.setVisible(context.session().can(Permission.BILLING));
        navBilling.setManaged(navBilling.isVisible());
        navReports.setVisible(context.session().can(Permission.REPORTS));
        navReports.setManaged(navReports.isVisible());
        navSettings.setVisible(context.session().can(Permission.MANAGE_USERS_SETTINGS));
        navSettings.setManaged(navSettings.isVisible());
        newPatientButton.setVisible(context.session().can(Permission.PATIENT_EDIT));
        newPatientButton.setManaged(newPatientButton.isVisible());
    }

    private void applyLanguage() {
        I18n.setLanguage(context.settings().profile().language());
        navDashboard.setText("  " + I18n.t("nav.dashboard"));
        navPatients.setText("  " + I18n.t("nav.patients"));
        navAppointments.setText("  " + I18n.t("nav.appointments"));
        navEncounters.setText("  " + I18n.t("nav.encounters"));
        navPrescriptions.setText("  " + I18n.t("nav.prescriptions"));
        navLabs.setText("  " + I18n.t("nav.labs"));
        navBilling.setText("  " + I18n.t("nav.billing"));
        navReports.setText("  " + I18n.t("nav.reports"));
        navSettings.setText("  " + I18n.t("nav.settings"));
        mainSectionLabel.setText(I18n.t("nav.main"));
        clinicalSectionLabel.setText(I18n.t("nav.clinical"));
        adminSectionLabel.setText(I18n.t("nav.admin"));
        logoutButton.setText(I18n.t("app.logout"));
        searchField.setPromptText(I18n.t("app.search"));
        newPatientButton.setText(I18n.t("app.newPatient"));
        pageTitle.setText(I18n.t("page.dashboard"));
        if (I18n.rightToLeft()) {
            Platform.runLater(() -> {
                if (contentArea.getScene() != null) {
                    contentArea.getScene().getRoot().setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
                }
            });
        }
    }

    @FXML
    private void onNav(ActionEvent event) {
        if (!(event.getSource() instanceof ToggleButton button)) {
            return;
        }
        String key = String.valueOf(button.getUserData());
        if (button.isSelected()) {
            navigate(key);
        } else {
            button.setSelected(true);
        }
    }

    private void navigate(String key) {
        try {
            context.navigator().show(key);
        } catch (RuntimeException e) {
            Dialogs.error(e);
        }
    }

    private void onNavigated(String key) {
        pageTitle.setText(I18n.t("page." + key));
        ToggleButton match = switch (key) {
            case "dashboard" -> navDashboard;
            case "patients", "patient-chart" -> navPatients;
            case "appointments" -> navAppointments;
            case "encounters", "encounter-editor" -> navEncounters;
            case "prescriptions" -> navPrescriptions;
            case "labs" -> navLabs;
            case "billing" -> navBilling;
            case "reports" -> navReports;
            case "settings" -> navSettings;
            default -> null;
        };
        if (match != null && match.isVisible()) {
            match.setSelected(true);
        }
    }

    @FXML
    private void onNewPatient() {
        if (!context.session().can(Permission.PATIENT_EDIT)) {
            Dialogs.warn("Your role cannot register patients");
            return;
        }
        PatientDialog.open(context, null, patient -> {
            Toasts.success("Patient " + patient.fullName() + " registered");
            context.navigator().show("patient-chart", patient.id());
        });
    }

    @FXML
    private void onLogout() {
        context.navigator().leaveCurrent(true, () -> {
            if (!Dialogs.confirm("Sign out", "Sign out of JSmartClinic?")) {
                return;
            }
            context.navigator().clearCache();
            context.auth().logout();
            onLogout.run();
        });
    }

    @FXML
    private void onLock() {
        showLockDialog();
    }

    private void setupSearch() {
        searchResults = new ContextMenu();
        searchField.textProperty().addListener((obs, old, value) -> {
            String query = value == null ? "" : value.trim();
            if (query.length() < 2) {
                searchResults.hide();
                return;
            }
            List<Patient> matches = context.patients().search(query, false, 0, 8);
            searchResults.getItems().clear();
            if (matches.isEmpty()) {
                MenuItem none = new MenuItem("No patients found");
                none.setDisable(true);
                searchResults.getItems().add(none);
            } else {
                for (Patient patient : matches) {
                    MenuItem item = new MenuItem(patient.fullName() + "   ·   " + patient.mrn()
                            + (patient.phone() == null ? "" : "   ·   " + patient.phone()));
                    item.setOnAction(e -> openChart(patient));
                    searchResults.getItems().add(item);
                }
            }
            if (searchField.getScene() != null && !searchResults.isShowing()) {
                searchResults.show(searchField, javafx.geometry.Side.BOTTOM, 0, 2);
            }
        });
        searchField.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && !searchResults.isFocused()) {
                searchResults.hide();
            }
        });
        searchField.setOnAction(e -> {
            if (!searchResults.getItems().isEmpty() && !searchResults.getItems().get(0).isDisable()) {
                searchResults.getItems().get(0).fire();
            }
        });
    }

    private void openChart(Patient patient) {
        searchField.clear();
        searchResults.hide();
        if (!context.session().can(Permission.VIEW_CLINICAL)) {
            Dialogs.info("Your role cannot open clinical charts.");
            return;
        }
        context.navigator().show("patient-chart", patient.id());
    }

    private void resetInactivity() {
        inactivity.playFromStart();
    }

    private void showLockDialog() {
        if (lockShowing || !context.session().loggedIn() || scene == null) {
            return;
        }
        lockShowing = true;
        // Save any in-progress draft first, but never make an automatic lock wait for a prompt.
        context.navigator().leaveCurrent(false, this::openLockDialog);
    }

    private void openLockDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Session locked");
        dialog.setHeaderText("Your session was locked after 10 minutes of inactivity.\nRe-enter your password to continue.");
        dialog.getDialogPane().getStyleClass().add("dialog-pane");
        PasswordField field = new PasswordField();
        field.setPromptText("Password");
        dialog.getDialogPane().setContent(field);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Platform.runLater(field::requestFocus);
        Optional<ButtonType> result = dialog.showAndWait();
        boolean unlocked = result.isPresent() && result.get() == ButtonType.OK
                && context.auth().verifyCurrentPassword(field.getText());
        lockShowing = false;
        if (unlocked) {
            resetInactivity();
        } else {
            context.navigator().clearCache();
            context.auth().logout();
            onLockExpired.run();
        }
    }

    private String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String second = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }
}
