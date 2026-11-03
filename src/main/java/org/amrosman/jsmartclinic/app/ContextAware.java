package org.amrosman.jsmartclinic.app;

/** Implemented by FXML controllers that need access to the application context. */
public interface ContextAware {
    void init(AppContext context);
}
