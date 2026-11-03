package org.amrosman.jsmartclinic.ui.components;

import javafx.geometry.Pos;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;

/** Lightweight toast notifications. */
public final class Toasts {

    private Toasts() {
    }

    public static void success(String message) {
        Notifications.create().title("Saved").text(message).position(Pos.BOTTOM_RIGHT)
                .hideAfter(Duration.seconds(3)).showInformation();
    }

    public static void info(String message) {
        Notifications.create().title("JSmartClinic").text(message).position(Pos.BOTTOM_RIGHT)
                .hideAfter(Duration.seconds(3)).showInformation();
    }

    public static void error(String message) {
        Notifications.create().title("Error").text(message).position(Pos.BOTTOM_RIGHT)
                .hideAfter(Duration.seconds(4)).showError();
    }
}
