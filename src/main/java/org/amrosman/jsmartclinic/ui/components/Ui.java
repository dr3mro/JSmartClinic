package org.amrosman.jsmartclinic.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import java.util.Locale;
import java.util.function.Function;

/** Small reusable UI factories. */
public final class Ui {

    private Ui() {
    }

    public static Label badge(String text) {
        Label label = new Label(text);
        label.getStyleClass().addAll("badge", "badge-" + css(text));
        return label;
    }

    public static String css(String text) {
        return text == null ? "unknown" : text.toLowerCase(Locale.ROOT).trim().replace(' ', '-').replace('_', '-');
    }

    public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> badgeCell() {
        return column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : badge(item));
            }
        };
    }

    public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> textCell(Function<S, String> value) {
        return column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        };
    }

    public static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-label");
        return label;
    }

    public static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        return label;
    }

    public static Button secondary(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    public static Button danger(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("danger-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    public static Button primary(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    /** A title + subtitle block used in table placeholders and empty states. */
    public static VBox emptyState(String title, String subtitle) {
        Label heading = new Label(title);
        heading.getStyleClass().add("empty-title");
        Label body = new Label(subtitle);
        body.getStyleClass().add("muted");
        VBox box = new VBox(6, heading, body);
        box.setAlignment(Pos.CENTER);
        box.setFillWidth(false);
        return box;
    }

    public static HBox spacerRow(Region... nodes) {
        HBox box = new HBox(10, nodes);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(nodes[nodes.length - 1], Priority.ALWAYS);
        return box;
    }
}
