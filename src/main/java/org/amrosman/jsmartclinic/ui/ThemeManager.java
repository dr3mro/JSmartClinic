package org.amrosman.jsmartclinic.ui;

import javafx.geometry.NodeOrientation;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

/** Applies the light/dark stylesheets and text direction to scenes and dialogs. */
public final class ThemeManager {

    private static final String LIGHT = "/org/amrosman/jsmartclinic/app.css";
    private static final String DARK = "/org/amrosman/jsmartclinic/app-dark.css";

    private static String currentTheme = "light";
    private static boolean rightToLeft;

    private ThemeManager() {
    }

    public static void apply(Scene scene, String theme, boolean rtl) {
        currentTheme = normalize(theme);
        rightToLeft = rtl;
        if (scene == null) {
            return;
        }
        scene.getStylesheets().setAll(sheets());
        Parent root = scene.getRoot();
        if (root != null) {
            root.setNodeOrientation(rtl ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        }
    }

    public static void applyToDialog(DialogPane pane) {
        if (pane == null) {
            return;
        }
        pane.getStylesheets().setAll(sheets());
        pane.setNodeOrientation(rightToLeft ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
    }

    public static String currentTheme() {
        return currentTheme;
    }

    public static boolean rightToLeft() {
        return rightToLeft;
    }

    public static String normalize(String theme) {
        return "dark".equalsIgnoreCase(theme) ? "dark" : "light";
    }

    private static java.util.List<String> sheets() {
        java.util.List<String> list = new java.util.ArrayList<>();
        java.net.URL light = ThemeManager.class.getResource(LIGHT);
        if (light != null) {
            list.add(light.toExternalForm());
        }
        if ("dark".equals(currentTheme)) {
            java.net.URL dark = ThemeManager.class.getResource(DARK);
            if (dark != null) {
                list.add(dark.toExternalForm());
            }
        }
        return list;
    }
}
