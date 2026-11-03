package org.amrosman.jsmartclinic.ui.auth;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.app.ContextAware;
import org.amrosman.jsmartclinic.domain.User;
import org.amrosman.jsmartclinic.util.I18n;
import org.amrosman.jsmartclinic.util.PasswordHasher;

import java.util.function.Consumer;

public class FirstRunController implements ContextAware {

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Label clinicLabel;
    @FXML private Label fullNameLabel;
    @FXML private Label usernameLabel;
    @FXML private Label passwordLabel;
    @FXML private Label confirmLabel;
    @FXML private Label errorLabel;
    @FXML private TextField clinicField;
    @FXML private TextField fullNameField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmField;
    @FXML private Button createButton;

    private AppContext context;
    private Consumer<User> onCreated = user -> {
    };

    @Override
    public void init(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        if (context == null) {
            context = AppContext.get();
        }
        I18n.setLanguage(context.settings().profile().language());
        titleLabel.setText(I18n.t("firstrun.title"));
        subtitleLabel.setText(I18n.t("firstrun.subtitle"));
        clinicLabel.setText(I18n.t("firstrun.clinic"));
        fullNameLabel.setText(I18n.t("firstrun.fullName"));
        usernameLabel.setText(I18n.t("firstrun.username"));
        passwordLabel.setText(I18n.t("firstrun.password"));
        confirmLabel.setText(I18n.t("firstrun.confirm"));
        createButton.setText(I18n.t("firstrun.button"));
        clinicField.setText(context.settings().clinicName());
        fullNameField.requestFocus();
    }

    public void setOnCreated(Consumer<User> onCreated) {
        this.onCreated = onCreated;
    }

    @FXML
    private void onCreate() {
        showError(null);
        String password = passwordField.getText();
        if (!password.equals(confirmField.getText())) {
            showError("Passwords do not match");
            return;
        }
        if (password.length() < PasswordHasher.MIN_LENGTH) {
            showError("Password must be at least " + PasswordHasher.MIN_LENGTH + " characters");
            return;
        }
        try {
            User admin = context.auth().createFirstAdministrator(clinicField.getText(), fullNameField.getText(),
                    usernameField.getText(), password);
            onCreated.accept(admin);
        } catch (RuntimeException e) {
            showError(e.getMessage());
        }
    }

    private void showError(String message) {
        errorLabel.setVisible(message != null);
        errorLabel.setManaged(message != null);
        errorLabel.setText(message == null ? "" : message);
    }
}
