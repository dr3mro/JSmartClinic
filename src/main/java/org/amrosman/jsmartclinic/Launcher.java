package org.amrosman.jsmartclinic;

/** Plain entry point so the packaged application does not need JavaFX on the classpath at launch. */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        javafx.application.Application.launch(HelloApplication.class, args);
    }
}
