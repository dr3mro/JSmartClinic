package org.amrosman.jsmartclinic.app;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Loads and caches views into the main shell's content area. */
public final class Navigator {

    /** Optional controller contract for views that accept a parameter (patient id, encounter id…). */
    public interface ParamAware {
        void setParam(Object param);
    }

    /** Optional controller contract for views that need to refresh when re-shown. */
    public interface Refreshable {
        void refresh();
    }

    private final AppContext context;
    private final Map<String, Parent> cache = new HashMap<>();
    private final Map<String, String> routes = new HashMap<>();
    private StackPane content;
    private Consumer<String> onNavigate = key -> {
    };

    public Navigator(AppContext context) {
        this.context = context;
        routes.put("dashboard", "views/dashboard.fxml");
        routes.put("patients", "views/patients.fxml");
        routes.put("appointments", "views/appointments.fxml");
        routes.put("encounters", "views/encounters.fxml");
        routes.put("prescriptions", "views/prescriptions.fxml");
        routes.put("labs", "views/labs.fxml");
        routes.put("billing", "views/billing.fxml");
        routes.put("reports", "views/reports.fxml");
        routes.put("settings", "views/settings.fxml");
        routes.put("patient-chart", "views/patient-chart.fxml");
        routes.put("encounter-editor", "views/encounter-editor.fxml");
    }

    public void attach(StackPane content) {
        this.content = content;
    }

    public void setOnNavigate(Consumer<String> onNavigate) {
        this.onNavigate = onNavigate;
    }

    public void show(String key) {
        show(key, null);
    }

    public void show(String key, Object param) {
        if (content == null) {
            throw new IllegalStateException("Navigator has no content area");
        }
        String route = routes.get(key);
        if (route == null) {
            throw new IllegalArgumentException("Unknown view: " + key);
        }
        Parent view = cache.get(key);
        if (param != null || view == null) {
            try {
                FXMLLoader loader = new FXMLLoader(
                        context.getClass().getResource("/org/amrosman/jsmartclinic/" + route));
                loader.setControllerFactory(context::controller);
                view = loader.load();
                Object controller = loader.getController();
                view.getProperties().put("controller", controller);
                if (param != null && controller instanceof ParamAware aware) {
                    aware.setParam(param);
                }
                if (controller instanceof Refreshable refreshable) {
                    refreshable.refresh();
                }
                if (param == null) {
                    cache.put(key, view);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Unable to load view " + key, e);
            }
        } else {
            Object controller = view.getProperties().get("controller");
            if (controller instanceof Refreshable refreshable) {
                refreshable.refresh();
            }
        }
        content.getChildren().setAll(view);
        onNavigate.accept(key);
    }

    public void refresh(String key) {
        Parent view = cache.remove(key);
        if (view == null && content != null && !content.getChildren().isEmpty()) {
            view = (Parent) content.getChildren().get(0);
        }
    }

    public void clearCache() {
        cache.clear();
        if (content != null) {
            content.getChildren().clear();
        }
    }
}
