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

import java.util.function.Consumer;

public class LoginController implements ContextAware {

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Label clinicNameLabel;
    @FXML private Label usernameLabel;
    @FXML private Label passwordLabel;
    @FXML private Label footerLabel;
    @FXML private Label errorLabel;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;

    private AppContext context;
    private Consumer<User> onLoggedIn = user -> {
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
        applyLanguage();
        clinicNameLabel.setText(context.settings().clinicName());
        usernameField.requestFocus();
    }

    private void applyLanguage() {
        titleLabel.setText(I18n.t("login.title"));
        subtitleLabel.setText(I18n.t("login.subtitle"));
        usernameLabel.setText(I18n.t("login.username"));
        passwordLabel.setText(I18n.t("login.password"));
        loginButton.setText(I18n.t("login.button"));
        footerLabel.setText(I18n.t("login.footer"));
        javafx.application.Platform.runLater(() -> {
            if (titleLabel.getScene() != null) {
                titleLabel.getScene().getRoot().setNodeOrientation(I18n.rightToLeft()
                        ? javafx.geometry.NodeOrientation.RIGHT_TO_LEFT
                        : javafx.geometry.NodeOrientation.LEFT_TO_RIGHT);
            }
        });
    }

    public void setOnLoggedIn(Consumer<User> onLoggedIn) {
        this.onLoggedIn = onLoggedIn;
    }

    @FXML
    private void onLogin() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        try {
            User user = context.auth().login(usernameField.getText(), passwordField.getText());
            passwordField.clear();
            onLoggedIn.accept(user);
        } catch (RuntimeException e) {
            errorLabel.setText(e.getMessage());
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
            passwordField.clear();
            passwordField.requestFocus();
        }
    }
}
