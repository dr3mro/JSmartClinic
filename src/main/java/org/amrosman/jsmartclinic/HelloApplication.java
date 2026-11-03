package org.amrosman.jsmartclinic;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.app.Navigator;
import org.amrosman.jsmartclinic.ui.auth.LoginController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Application entry point. Initialises the {@link AppContext}, shows the first-run
 * wizard or the login screen, and swaps to the main shell after a successful login.
 */
public class HelloApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(HelloApplication.class);

    private Stage stage;
    private AppContext context;

    @Override
    public void start(Stage stage) throws IOException {
        this.stage = stage;
        this.context = AppContext.initialise();

        stage.setTitle("JSmartClinic");
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        showAuthScreen();
        stage.show();
    }

    private void showAuthScreen() throws IOException {
        if (context.users().count() == 0) {
            showFirstRun();
        } else {
            showLogin();
        }
    }

    private void showFirstRun() throws IOException {
        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("views/first-run.fxml"));
        loader.setControllerFactory(context::controller);
        Parent root = loader.load();
        org.amrosman.jsmartclinic.ui.auth.FirstRunController controller = loader.getController();
        controller.setOnCreated(user -> {
            try {
                showLogin();
            } catch (IOException e) {
                log.error("Unable to open login screen", e);
            }
        });
        setScene(root, 720, 720);
    }

    private void showLogin() throws IOException {
        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("views/login.fxml"));
        loader.setControllerFactory(context::controller);
        Parent root = loader.load();
        LoginController controller = loader.getController();
        controller.setOnLoggedIn(user -> showMain());
        setScene(root, 1000, 700);
    }

    private void showMain() {
        try {
            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("views/main-view.fxml"));
            loader.setControllerFactory(context::controller);
            Parent root = loader.load();
            org.amrosman.jsmartclinic.ui.MainController controller = loader.getController();
            Scene scene = setScene(root, 1360, 860);
            controller.installShortcuts(scene);
            controller.setOnLogout(() -> {
                try {
                    showLogin();
                } catch (IOException e) {
                    log.error("Unable to return to login", e);
                }
            });
            controller.setOnLockExpired(() -> {
                try {
                    showLogin();
                } catch (IOException e) {
                    log.error("Unable to return to login", e);
                }
            });
            stage.setTitle("JSmartClinic — " + context.settings().clinicName());
        } catch (IOException e) {
            log.error("Unable to open main window", e);
            throw new IllegalStateException(e);
        }
    }

    private Scene setScene(Parent root, double width, double height) {
        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(root, width, height);
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
        var profile = context.settings().profile();
        org.amrosman.jsmartclinic.util.I18n.setLanguage(profile.language());
        org.amrosman.jsmartclinic.ui.ThemeManager.apply(scene, profile.theme(),
                org.amrosman.jsmartclinic.util.I18n.rightToLeft());
        stage.setWidth(width);
        stage.setHeight(height);
        stage.centerOnScreen();
        return scene;
    }

    @Override
    public void stop() {
        if (context != null) {
            context.shutdown();
        }
    }

    /** Renders a standalone view (used for the prototype navigation targets). */
    public static Parent loadView(String resource) throws IOException {
        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource(resource));
        loader.setControllerFactory(AppContext.get()::controller);
        return loader.load();
    }

    /** Not used directly, kept for the Navigator contract. */
    static Navigator navigator() {
        return AppContext.get().navigator();
    }
}
